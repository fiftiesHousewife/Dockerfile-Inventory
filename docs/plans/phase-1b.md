# Phase 1b — Lineage and effective images: plan

Status: questions answered 6 Nov. Slices 1 to 7 built 6 Nov. Slices are built test-first, as in Phases 0 and 1.

Phase 1b rebuilds the image family tree from Artifactory REST, links internal base images to the Dockerfiles that
built them by fingerprint, folds inherited properties into an effective image per Dockerfile, and publishes the gap
list: images no scanned Dockerfile matches. Module: `lineage`. Inputs: Artifactory REST (never AQL), `facts.jsonl`,
`manifest.jsonl`. Gate: lineage for at least 95% of Artifactory images; every internal base linked at strong tier
or queued for curation; gap list published and reviewed.

## Answers (6 Nov)

| # | Question | Answer | What it changes |
| --- | --- | --- | --- |
| Q1 | Artifactory access | No token yet | Slices 1-3 are built against Artifactory's documented response shapes; recorded responses replace them once access exists. The gate waits for access |
| Q2 | Retention | Nothing is deleted | A missing parent is a real finding (squashed image or stale child), not a deleted tag |
| Q3 | Multi-arch | `linux/amd64` only | Manifest lists use their amd64 entry; images with no amd64 entry are skipped and counted in the rates |
| Q4 | History samples | Use public Buildah-built images | The history normaliser is written from public UBI and other Buildah-built images' `created_by` strings, then checked against ours once access exists |
| Q5 | Internal bases | Any image another image is built on | Worked out from the layer tree; no list to maintain |
| Q6 | Remote repositories | None | Only local repositories are read |
| Q7 | Labels | None carried reliably | No label-based links. See D2 |
| Q8 | External bases (UBI) | Mirrored into a local repository | Mirrored images appear in the tree as roots. See D1 |
| Q9 | Which tags (slice 2) | Every tag | One manifest request per tag; configs fetched once per digest |
| Q10 | Promoted images (slice 2) | One image, every reference | One node per config digest, listing each repository and tag; counts are per distinct image |
| Q11 | Ambiguous parents (slice 3) | Report them | Both candidates recorded; links and the fold use the earliest-pushed one; rates count them |
| Q12 | Children of an outdated base build | Report, in slice 7 | "Built on an outdated base": the parent is not the newest push of its image name |
| Q13 | Is the Tekton build Konflux-like (slice 4)? | Not known | Strip the known `cachi2` prefix from `RUN`, mark known builder-added steps rather than dropping them, and read revision labels in slice 5 when present. Check against real history once Artifactory access exists |
| Q14 | Where each Dockerfile's steps come from (slice 5) | `facts` writes them | `facts.jsonl` rows gain `steps` and `fromPath`; a contract fixture keeps both normal forms in step |
| Q15 | How Dockerfiles name Artifactory images in `FROM` | Mixed, not known | A `FROM` path is matched against Artifactory image names as written and with its first segment removed (a repository key); the rates say which form matched |
| Q16 | Revision labels (slice 5) | Never present in the estate | Not read. `STRONG` comes from the release index alone |
| Q17 | Identical Dockerfiles in several repositories | Link to the group | One link to every Dockerfile with the same steps, listing each location |
| Q18 | ONBUILD in bases (slice 6) | Rare or none | A child of a base with `OnBuild` triggers is flagged and counted, not modelled |
| Q19 | Fold granularity (slice 6) | Per image | One effective record per config digest; a Dockerfile's view is its newest linked image, chosen in the report |

## Decisions that follow (say if any is wrong)

| # | Decision | Why |
| --- | --- | --- |
| D1 | A root image (no parent in the tree) that no Dockerfile links to is reported as a mirrored external base, not as a gap. The gap list holds only non-root images with no link | Mirrored UBI and similar images have no Dockerfile here and should not swamp the gap list |
| D2 | With no labels, the tiers are: strong (one candidate Dockerfile whose `FROM` matches the parent and whose instructions align with the image's own history, plus a matching release-database commit for its repository), probable (unique alignment without that corroboration), weak (several candidates, or partial alignment; curation queue). The plan's "exact" tier is dropped | The exact tier needed labels or a release record tied to an image, and neither exists |

## Slices (test list per slice is written before its code)

1. **Artifactory client** behind an interface, with fakes built from documented shapes until recordings exist:
   REST only, never AQL (decided 6 Nov). Local Docker repositories from `GET /api/repositories?type=local&packageType=docker`;
   per repository, images from the Docker v2 catalogue `GET /api/docker/<repo>/v2/_catalog` and tags from
   `.../v2/<image>/tags/list`, both paged by `n` and `last`; manifests and config blobs from
   `.../v2/<image>/manifests/<tag>` and `.../v2/<image>/blobs/<digest>`, accepting manifest lists so the amd64
   entry can be chosen; last push from `GET /api/storage/<repo>/<image>/<tag>`. Dedupe on config digest before
   fetching. The call count grows with tags, so it runs with the same bounded concurrency and rate-limit retry as
   discovery, and resumes.
   Slice 1 builds only the client; concurrency, dedupe and resume belong to the run (slice 8). Its tests, in order,
   against a local HTTP server serving hand-written fixtures in `src/test/resources/fixtures/artifactory`:
   1. Local Docker repositories are listed by key, with the token sent as `Authorization: Bearer`.
   2. Images come from the catalogue, paged with `n` and `last` until a page comes back short.
   3. Tags page the same way; a missing or null `tags` and a 404 (image gone) give no tags.
   4. A manifest is fetched with every Docker and OCI manifest and index type accepted, and comes back with its
      media type (`Content-Type`), digest (`Docker-Content-Digest`) and body; 404 gives empty.
   5. A blob comes back as text, following Artifactory's redirect; 404 gives empty.
   6. Last push is the storage API's `lastModified`.
   7. A 429 or 503 is retried after `Retry-After`, then the answer is returned.
   8. Any other status, or unreadable JSON, is an `ArtifactoryException` naming the method, URL and status, never the token.

   As built: test 3 passed when first written, because a 404 body has no `tags` field. Test 8 later made other
   statuses throw, and test 3 then held 404 as "no tags". The retry decision (`Retry`) has its own tests; with no
   `Retry-After` it waits 5 seconds, for at most six attempts. To check against the real service: whether a blob
   redirect goes to a different host (S3 or another store), since `java.net.http` re-sends the `Authorization`
   header on a redirect, and some stores reject a request that carries one.

2. **Image nodes**: reference, config digest, `rootfs.diff_ids`, history, user, labels. Manifest lists take their
   `linux/amd64` entry (Q3).

   Design: `ImageNodes.resolve(artifactory, repository, image, tag)` returns a sealed `Resolved`: `Node(configDigest,
   ImageConfig)` or `Skipped(reason)`. `ImageConfig.parse(text)` reads the config: `rootfs.diff_ids`, history
   steps (`created_by`, `empty_layer`, `comment`, `created`), `config.User`, `config.Labels`. The node's identity
   is its config digest, so a tag, a second tag, and the same image promoted into another repository are one node
   with several references. Dedupe across references is the run's job (slice 8), which skips the config fetch for
   a digest it has seen; this slice only resolves one reference. Pure parsing is kept apart from fetching, so
   `ImageConfig` is tested on text alone. Tests, in order:
   1. A config's diff IDs, user and labels are read; missing `User` and `Labels` give none.
   2. History steps keep `created_by`, `comment` and `empty_layer` (absent means false), in order.
   3. History steps that make layers match the diff IDs in number; a config where they do not is still read, and
      says so (`historyMatchesLayers` false), for slice 4 to fall back on.
   4. A single Docker v2 or OCI manifest resolves through its `config.digest` to a node.
   5. A manifest list or OCI index resolves through its `linux/amd64` entry's manifest to a node.
   6. A list with no `linux/amd64` entry is `Skipped(NO_AMD64)`.
   7. A schema 1 manifest (old Docker, no config) is `Skipped(SCHEMA_1)`.
   8. A tag, manifest or config that has gone between listing and fetching is `Skipped(GONE)`.
   9. A config that is not JSON, or has no `rootfs`, is `Skipped(UNREADABLE_CONFIG)`, with the digest, so one bad
      image does not stop the run.

   As built: a list or index is recognised by its `manifests` array and schema 1 by `schemaVersion`, not by
   `Content-Type`, so a mislabelled response is still read correctly. `ImageNodes.node` (config digest to node)
   is separate, so the run can skip it for a digest already seen. `Skipped.detail` is the tag reference, or the
   digest that went missing. A `User` of `""` is kept as written; slice 6 decides what it means (root).
3. **Layer-prefix lineage**: parent = the node whose full `diff_ids` list is this one's longest strict prefix. A
   non-root image with no parent is a finding (Q2).

   Design: `Lineage.of(nodes)` takes every resolved `Node` and returns each one's `Parent`: `Root`, `Of(digest)`
   or `Ambiguous(candidates)`. Each node gets the OCI chain ID of every layer prefix (`sha256(prev + " " +
   diffId)`), indexed once, so finding a parent is one lookup per layer rather than a comparison with every other
   image. A child that adds only metadata (`USER`, `LABEL`, `ENV`) has exactly its parent's layers, so equal
   layers count too, provided the candidate's history is a strict prefix of the child's: Buildah copies the
   base's history into the child unchanged. Whether a root *should* have had a parent depends on the Dockerfile
   its image was built from, so the "non-root with no parent" finding is made in slice 5, not here. Tests, in
   order:
   1. An image whose first layer no other image has is a root.
   2. The parent is the image whose layers are the longest strict prefix: a child of a child picks its parent,
      not its grandparent.
   3. Siblings that share a parent's layers and then diverge are not each other's parent.
   4. A child that adds only metadata takes the image with the same layers whose history is a strict prefix of
      its own; that image does not take the child as its parent.
   5. Two images with the same layers and the same history are not each other's parent; an image built on them
      is ambiguous, naming both.
   6. An image with no layers is a root and is no image's parent.

   As built: among candidates at the longest matching layer prefix, those whose history leads to this image
   are preferred, and the one with the longest history is the nearest ancestor. So `base`, then `base` plus a
   `LABEL`, then an image built on the labelled one, form a chain, not three children of `base`. The test for
   this was added after the code, and changing the code on purpose made it fail. Test 3 (siblings) passed when
   first written, as the chain-ID lookup already covered it. Where no candidate's history leads to the image (a
   squashed image, or a builder that writes no history), all candidates at that prefix are considered.
4. **History normaliser**, from public Buildah-built `created_by` samples (Q4): `RUN` text with variable spans
   wildcarded, `COPY` to its destination, metadata steps to keyword and key.

   What the real history shows (6 Nov; `ubi9/ubi-minimal`, `ubi9/ubi`, `ubi9/python-311`, `ubi9/nodejs-20` from
   `registry.access.redhat.com`, built by Red Hat's Tekton-based Konflux):
   - Every Dockerfile instruction except `FROM` and `ARG` leaves a step. Metadata steps are
     `/bin/sh -c #(nop) <KEYWORD> <as written>`; `RUN` is `/bin/sh -c <command>`.
   - **Variables are not expanded**: `$NODEJS_VERSION`, `${HOME}` and `$STI_SCRIPTS_PATH` appear as written in the
     Dockerfile. So no wildcarding is needed, and steps can be compared with the Dockerfile text exactly once
     whitespace is collapsed. Backslash-newline continuations are joined, with their indentation kept.
   - `COPY` is `COPY dir:<hash> in <dest>` or `COPY file:<hash> in <dest>`; the hash is of content, so only the
     destination can be compared.
   - **The first step of each build carries the comment `FROM <base reference>`**, the base as resolved at build
     time, such as `registry.access.redhat.com/ubi9/s2i-core:9.8-1779830904`. That names the parent build directly.
   - Squashed builds: almost every step is marked `empty_layer`, and only each build's last step makes a layer,
     so one build is one layer. Slice 3's prefix rule still holds.
   - Konflux adds things no Dockerfile has: `. /cachi2/cachi2.env && ` at the start of every `RUN`; four
     `COPY ... /buildinfo/...` steps; and a final `LABEL` holding `build-date`, `vcs-ref` and
     `org.opencontainers.image.revision`.

   Design: `HistoryNormaliser.normalise(HistoryStep)` returns `NormalStep(keyword, text, from)`. The keyword is
   `RUN`, `COPY`, `ADD`, a metadata keyword, or `OTHER`. The text is the comparable part: the command for `RUN`,
   the destination for `COPY`/`ADD`, sorted key names for `LABEL`/`ENV`, and the collapsed value otherwise.
   `from` is the base reference when the step's comment is `FROM ...`. The four public configs' histories are
   kept as fixtures in `src/test/resources/fixtures/history/`. Slice 5 needs the same normal form for each
   Dockerfile's instructions; where that comes from (a new `facts` output, since only `facts` may import
   OpenRewrite) is slice 5's first question. Tests, in order:
   1. `RUN` gives its command, `/bin/sh -c ` removed and whitespace collapsed.
   2. Known builder prefixes on `RUN` are removed (Q13).
   3. `COPY` and `ADD` give their destination, variables kept, trailing whitespace dropped.
   4. `LABEL` gives its sorted key names; quoted keys are unquoted, and quoted values with spaces or escaped quotes
      do not split.
   5. `ENV` gives its key names, in both `ENV k=v ...` and the legacy `ENV k v` form.
   6. `USER`, `WORKDIR`, `EXPOSE`, `ENTRYPOINT`, `CMD`, `VOLUME`, `STOPSIGNAL`, `SHELL` and `ONBUILD` give the keyword
      and the collapsed value.
   7. Anything else is `OTHER`, with the collapsed text.
   7b. Known builder-added steps are marked `builderAdded` (Q13): `COPY` into `/usr/share/buildinfo/` or
       `/root/buildinfo/`, and a `LABEL` whose keys include `build-date` and `vcs-ref`.
   8. A `FROM <reference>` comment becomes `from`.
   9. Every step in the four public images normalises to something other than `OTHER`.

   As built: test 9 passed when first written, since tests 1 to 8 were written from the same strings; removing
   `WORKDIR` from the recognised keywords made it fail for two of the images. `HEALTHCHECK` and `ARG` are not
   recognised: Buildah left neither in the samples, so they stay `OTHER` until real history shows them. Exec-form
   values (`CMD ["a", "b"]`) are kept as written; whether Buildah rewrites their spacing is unknown, so slice 5
   compares them with JSON whitespace ignored.
5. **Dockerfile links**: candidates whose resolved final `FROM` matches the lineage parent; ordered alignment of the
   image's own history steps against each candidate's instructions; tiers per D2.

   Evidence (6 Nov): upstream `sclorg/s2i-python-container` `3.11/Dockerfile.rhel9` (commit `b30d258`) against the
   public `ubi9/python-311` history. Every instruction after `FROM` appears in order. `RUN` text is joined at
   backslash-newlines, with comment lines inside the command dropped and nothing else changed. The `FROM`
   comment is `registry.access.redhat.com/ubi9/s2i-base:9.8-1779892026`, where the Dockerfile says
   `ubi9/s2i-base:1`: the build pins the tag and adds the registry, so bases match on image path, not on host or
   tag.

   Two parts, each test-first:

   **5a, in `facts`: each Dockerfile's steps.** `facts.jsonl` rows gain `steps`: the final image's instructions
   in the same normal form as `NormalStep` (`keyword`, `text`), and `fromPath`: the resolved final base's image
   path without registry host or tag. The final image's instructions are the final stage's, preceded by those of
   any earlier stage it is built `FROM`. `FROM` and `ARG` leave no step, as in Buildah's history. The two modules
   must agree on the normal form without sharing code, since stages hand files, not objects. So a contract
   fixture, `testdata/contracts/python-311.steps.json`, is the expected steps of the real pair above (builder
   steps removed). `facts` tests that the Dockerfile produces it, and `lineage` tests that the history does.
   1. `RUN` (shell form): continuations joined, comment lines inside dropped, whitespace collapsed.
   2. `RUN` exec form and heredoc: kept as written and collapsed (no real history sample; they will rarely match).
   3. `COPY`/`ADD`: the destination (the last argument, or the exec array's last element); flags ignored.
   4. `LABEL`/`ENV`: sorted key names; other instructions: keyword and collapsed value.
   5. A final stage built `FROM` an earlier stage starts with that stage's steps; `ARG`, `FROM`, comments and
      blank lines leave none.
   6. `fromPath`: `registry.example.com:5000/ubi9/s2i-base:1@sha256:...` gives `ubi9/s2i-base`; a first segment
      with no dot, colon or `localhost` is part of the path, not a host (Docker's rule). Whether a first path
      segment is a repository key is decided in 5b, against Artifactory's names (Q15).
   7. Contract: the upstream python 3.11 Dockerfile gives exactly the contract steps.

   As built (5a, 6 Nov): each instruction is printed from the parsed tree and reduced by keyword, so forms the
   grammar distinguishes (exec, heredoc) are kept as written; test 2 passed when first written for that reason.
   The contract test found a bug in Phase 1's parser adapter. A comment line inside a continued `RUN`, which Docker
   allows and Red Hat's python 3.11 Dockerfile has, was recorded as a parse failure: the check for lines the
   parser glues onto an argument now lets a newline that ends a comment line through. A stage name resolves only
   to earlier stages, as in Docker, so `FROM app AS app` cannot loop; that test needed a pre-emptive timeout to
   fail cleanly. Rows written before this change read back with no steps. No real facts run exists yet, but one
   made before this change should be rerun into an empty `OUT_DIR`.

   **5b, in `lineage`: links.** `Links.of(nodes, parents, dockerfiles)` gives each image a `Link`: tier `STRONG`,
   `PROBABLE` or `WEAK` with the matching Dockerfile group, or `NONE`. A group is every Dockerfile (by blob SHA)
   with the same steps (Q17), so copies across repositories link once.
   8. An image's own steps are the history after its parent's; with no parent, the steps from its last `FROM`
      comment; builder-added steps are dropped.
   9. Contract: the public python-311 history gives exactly the contract steps.
   10. An image whose own steps equal a group's steps links to it.
   11. Tier: `STRONG` when the group has a location at a released commit (D2, Q16); `PROBABLE` otherwise.
   12. No exact match: the best ordered partial alignment (longest common subsequence over at least 80% of the
       longer side) among groups whose `fromPath` matches the parent's or the `FROM` comment's image path gives
       `WEAK`, naming every group tied for best.
   13. Nothing aligns: `NONE`.
   14. A root whose linked Dockerfile names a base that should be in Artifactory is a "parent missing" finding (Q2);
       the base should be there when its `fromPath`, as written or without its first segment (Q15), is the name of
       some image in Artifactory.

   As built (5b, 6 Nov): an image's base path for weak links comes from its last `FROM` comment. An image whose
   history records none gets no weak link, since scoring it against every Dockerfile would cost too much and mean
   little; slice 8 can add the parent's Artifactory names once references reach the run. The contract test on
   this side passed when first written, as the contract was derived from the same history. It now holds the two
   normal forms together. `ImagePaths` was tested after the code: two of three deliberate breaks survived until
   cases with a port-only host and a port without a tag were added. An ambiguous parent's first candidate gives
   the own steps; the candidates share history, so any would do.
6. **Effective fold**: walk each Dockerfile's chain to the root, recording which Dockerfile contributed the user,
   `ENV`, labels, `WORKDIR`, `ENTRYPOINT`, `CMD` and packages; `ONBUILD` injected at the child's start.

   Design: the effective values are already in each image's config (`User`, `Env`, `Labels`, `WorkingDir`,
   `Entrypoint`, `Cmd`), since the builder folds them. What the fold adds is *who set each one*, which Phase 3's
   effective rules need ("the final user is root, set by base X's Dockerfile"). `Fold.of(images, parents, links)`
   gives each image an `EffectiveImage`:
   - `chain`: itself, then each ancestor to the root, each with its `Link`.
   - Each property's value from the image's own config, and `setBy`: the nearest image in the chain whose own
     steps (slice 5) set it, with that image's link. Alternatively the external base named in a root's `FROM`
     comment, when only the steps before that comment set it, or unknown when no recorded step sets it.
   - `ENV` and labels per key, so a child overriding one key takes only that key.

   Packages are not repeated here: the chain names each Dockerfile group, and the report joins them with
   `facts.jsonl`. Tests, in order:
   1. `ImageConfig` also reads `Env`, `WorkingDir`, `Entrypoint`, `Cmd` and `OnBuild`.
   2. A property the image's own steps set is attributed to the image and its link.
   3. A property the image does not set is attributed to the nearest ancestor that does.
   4. `ENV` and labels are attributed per key; a child that overrides one key takes that key only.
   5. A property set only before a root's `FROM` comment is attributed to the external base the comment names.
   6. A property no recorded step sets is unknown.
   7. An ambiguous parent is walked through its first candidate, as in slice 5.
   8. The chain lists every ancestor with its link, and a repeated image ends the walk.
   9. A child whose parent's config has `OnBuild` triggers is flagged `parentHasOnbuild` (Q18).

   As built: `ImageConfig` gained the runtime fields, with a shorter constructor kept for the tree and links,
   which need only layers and history. Builder-added label keys (`build-date`, `vcs-ref`) are attributed as
   unknown, since their steps are dropped from every image's own steps. Tests 6 and 7 passed when first written,
   since the default and the first-candidate walk were already there; breaking each made its test fail. A chain
   entry for an image missing from the links map carries no link; the run (slice 8) links every image first.
7. **Gap list** `gaps.jsonl` (non-root images with no link, D1) and `external-bases.jsonl` (unlinked roots), with
   repository, image, tags and last push. Also `outdated-bases.jsonl` (Q12): images whose parent is not the newest
   push of that parent's image name.

   Design: the run knows each node's references (repository, image name, tag, last push); `Gaps.of(images,
   parents, links)` takes each node with its references and returns three lists. "Image name" means the
   Artifactory image name without the repository, so a build promoted from `docker-dev` to `docker-prod` is one
   name (Q10). Tests, in order:
   1. A non-root image with no link is a gap, with its references and latest push.
   2. A root with no link is an external base (D1), not a gap.
   3. A linked image, at any tier, is neither.
   4. An image whose parent is not the newest push of the parent's image name is outdated, naming the newer image.
   5. The same image name in two repositories is one name.
   6. A parent that is the newest push is not outdated; for an ambiguous parent, it is enough that any candidate is.

   As built: test 6 found that a parent whose references carry no push time was reported as outdated, with no
   newer image named; such names are now skipped. Breaking the one-name rule (naming images with their
   repository) made the promotion test fail. Writing the three lists to `gaps.jsonl`, `external-bases.jsonl` and
   `outdated-bases.jsonl` belongs to the run (slice 8).
8. **Run and rates**: lineage coverage, link tiers, gap count, skipped non-amd64 images.

   Three parts, each test-first. Inputs and outputs live under one `OUT_DIR`, as in the other stages.

   **8a, inventory** (`Inventory`): for every tag of every image of every local Docker repository, find the
   config digest (manifest, then the amd64 entry of a list), fetch the config only if `configs/<digest>.json` is
   not already stored, and append a row to `images.jsonl`: repository, image, tag, last push, then the config
   digest or the skip reason and detail. Images run on virtual threads behind a semaphore, as in discovery. A rerun
   skips tags already recorded. `ImageNodes` is split so the config digest is known before the config is fetched.
   1. Every tag is recorded with its config digest and last push, and its config is stored.
   2. A skipped tag is recorded with its reason.
   3. A config is fetched once, however many tags and repositories share it.
   4. A rerun fetches no manifest for a tag already recorded.
   5. Several threads give the same rows as one.

   **8b, analysis** (`Analysis`): reads `images.jsonl`, the stored configs, `facts.jsonl` (steps, `fromPath`) and
   `manifest.jsonl` (blobs at a released commit), runs slices 3 and 5 to 7, and writes `lineage.jsonl` (each
   image's parent), `links.jsonl`, `effective.jsonl`, `gaps.jsonl`, `external-bases.jsonl`,
   `outdated-bases.jsonl` and `rates.json`.
   6. The tree, links and fold are written, one row per image.
   7. Gaps, external bases and outdated bases are written.
   8. Rates: tags, images, skips by reason, lineage coverage, ambiguous parents, links by tier, parent missing,
      children of `ONBUILD` bases, gaps, external bases, outdated bases. Lineage coverage, the gate's measure, is
      the share of tags in scope that were placed in the tree; tags skipped as `NO_AMD64` (Q3) or `GONE` are out
      of scope.

   **8c, `LineageMain`**: `ARTIFACTORY_URL`, `ARTIFACTORY_TOKEN`, `FACTS_DIR`, `DISCOVERY_DIR`, `OUT_DIR`,
   `[CONCURRENCY]`. It runs 8a then 8b. Untested, like the other mains.
## Phase 2 after this

Clustering needs only `facts.jsonl`, so it could run before 1b finishes; the effective user (from 1b) refines the
root versus non-root counts. Its own plan comes after 1b.
