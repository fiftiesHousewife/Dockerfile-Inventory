# Phase 1b — Lineage and effective images: draft plan

Status: draft, 6 Nov. No code until the questions below are answered (AGENTS.md: plan, then grill, then build).

Phase 1b rebuilds the image family tree from Artifactory REST, links internal base images to the Dockerfiles that
built them by fingerprint, folds inherited properties into an effective image per Dockerfile, and publishes the gap
list: images no scanned Dockerfile matches. Module: `lineage`. Inputs: Artifactory REST and AQL, `facts.jsonl`,
`manifest.jsonl`. Gate: lineage for at least 95% of Artifactory images; every internal base linked at exact or
strong tier or queued for curation; gap list published and reviewed.

## Proposed slices (test-first, as Phases 0 and 1)

1. **Artifactory client** behind an interface, recorded-response fakes: AQL for `manifest.json` and
   `list.manifest.json` per Docker repository, paged by `offset`; manifest and config blob downloads from the tag
   folder. Dedupe on config digest before fetching.
2. **Image nodes**: reference, config digest, `rootfs.diff_ids`, history, user, labels. Manifest lists pick one
   platform (Q3).
3. **Layer-prefix lineage**: parent = the node whose full `diff_ids` list is this one's longest strict prefix. No
   parent for a non-root image is a finding (squashed, deleted, or stale child).
4. **History normaliser**, from real Buildah `created_by` samples (Q4): `RUN` text with variable spans wildcarded,
   `COPY` to its destination, metadata steps to keyword and key.
5. **Dockerfile links**: candidates whose resolved final `FROM` matches the lineage parent; ordered alignment of
   the image's own history steps against each candidate's instructions; tiers exact (labels or release database),
   strong (unique alignment plus one corroborating signal), weak (curation queue).
6. **Effective fold**: walk each Dockerfile's chain to the root, recording which Dockerfile contributed the user,
   `ENV`, labels, `WORKDIR`, `ENTRYPOINT`, `CMD` and packages; `ONBUILD` injected at the child's start.
7. **Gap list** `gaps.jsonl`: images with no link at any tier, with repository, image, tags and last push.
8. **Run and rates**: lineage coverage, link tiers, gap count.

## Questions for you

| # | Question | Why it matters |
| --- | --- | --- |
| Q1 | Artifactory base URL, the Docker repository names (local, remote, virtual?), and is there a read token with AQL rights yet? | Slice 1 can start on documented shapes, but recorded fixtures and the gate need the real thing |
| Q2 | Roughly how many images and tags are in Artifactory? Is there a retention policy that deletes old tags? | Sizes the job; deleted parents make "no parent" findings that are not real problems |
| Q3 | Multi-arch images: scan `linux/amd64` only, or every platform? | One platform keeps the tree simple; arm64-only images would drop out |
| Q4 | Can you export ten real `created_by` history entries (Buildah-built) from one internal base and one leaf image? | The history normaliser must be written from real strings, not Docker's classic format |
| Q5 | Which images count as "internal bases": anything another image builds on, or a known list (golden images)? | Decides which images need a Dockerfile link at all |
| Q6 | Remote (proxied) repositories, such as a Docker Hub or Red Hat registry mirror: include their images as tree roots only, or leave them out? | External bases have no Dockerfile here; they should be roots, not gaps |
| Q7 | Which labels, if any, do some images carry reliably (`org.opencontainers.image.revision`, `source`, `vcs-ref`)? | Exact-tier links come from them |

## Phase 2 after this

Clustering needs only `facts.jsonl`, so it could run before 1b finishes; the effective user (from 1b) refines the
root versus non-root counts. Its own plan comes after 1b's questions are settled.
