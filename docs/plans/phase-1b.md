# Phase 1b — Lineage and effective images: plan

Status: questions answered 6 Nov. Slices 1 and 2 built 6 Nov. Slices are built test-first, as in Phases 0 and 1.

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
4. **History normaliser**, from public Buildah-built `created_by` samples (Q4): `RUN` text with variable spans
   wildcarded, `COPY` to its destination, metadata steps to keyword and key.
5. **Dockerfile links**: candidates whose resolved final `FROM` matches the lineage parent; ordered alignment of the
   image's own history steps against each candidate's instructions; tiers per D2.
6. **Effective fold**: walk each Dockerfile's chain to the root, recording which Dockerfile contributed the user,
   `ENV`, labels, `WORKDIR`, `ENTRYPOINT`, `CMD` and packages; `ONBUILD` injected at the child's start.
7. **Gap list** `gaps.jsonl` (non-root images with no link, D1) and `external-bases.jsonl` (unlinked roots), with
   repository, image, tags and last push. Also `outdated-bases.jsonl` (Q12): images whose parent is not the newest push of that parent's image name.
8. **Run and rates**: lineage coverage, link tiers, gap count, skipped non-amd64 images.

## Phase 2 after this

Clustering needs only `facts.jsonl`, so it could run before 1b finishes; the effective user (from 1b) refines the
root versus non-root counts. Its own plan comes after 1b.
