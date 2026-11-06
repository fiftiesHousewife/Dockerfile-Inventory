# Phase 1b — Lineage and effective images: plan

Status: questions answered 6 Nov; ready to build. Slices are built test-first, as in Phases 0 and 1.

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

2. **Image nodes**: reference, config digest, `rootfs.diff_ids`, history, user, labels. Manifest lists take their
   `linux/amd64` entry (Q3).
3. **Layer-prefix lineage**: parent = the node whose full `diff_ids` list is this one's longest strict prefix. A
   non-root image with no parent is a finding (Q2).
4. **History normaliser**, from public Buildah-built `created_by` samples (Q4): `RUN` text with variable spans
   wildcarded, `COPY` to its destination, metadata steps to keyword and key.
5. **Dockerfile links**: candidates whose resolved final `FROM` matches the lineage parent; ordered alignment of the
   image's own history steps against each candidate's instructions; tiers per D2.
6. **Effective fold**: walk each Dockerfile's chain to the root, recording which Dockerfile contributed the user,
   `ENV`, labels, `WORKDIR`, `ENTRYPOINT`, `CMD` and packages; `ONBUILD` injected at the child's start.
7. **Gap list** `gaps.jsonl` (non-root images with no link, D1) and `external-bases.jsonl` (unlinked roots), with
   repository, image, tags and last push.
8. **Run and rates**: lineage coverage, link tiers, gap count, skipped non-amd64 images.

## Phase 2 after this

Clustering needs only `facts.jsonl`, so it could run before 1b finishes; the effective user (from 1b) refines the
root versus non-root counts. Its own plan comes after 1b.
