# Lineage in the report, and fixtures from the local JCR run: plan

Status: slice A built 6 Nov; slice B waits on the recorded replies.

The local JCR run (`scripts/lineage-demo.sh`, BACKLOG Phase 1b) built four sclorg images from discovered
Dockerfiles and ran lineage against them: every parent right, every image linked to its own Dockerfile. The
report reads lineage only for effective users, so none of that shows on the dashboard; and lineage's Artifactory
fixtures are still hand-written from JFrog's documentation.

## Slice A: a lineage section in the report

When `LINEAGE_DIR` is given, the dashboard gains a "Lineage" section; without it, the section says lineage has not
run (as the final-user note does now).

- `Estate` (or a new `LineageView` beside it) reads `images.jsonl`, `lineage.jsonl`, `links.jsonl`,
  `external-bases.jsonl`, `gaps.jsonl`, `outdated-bases.jsonl` and `rates.json`.
- Tiles: images, lineage coverage, images linked to a Dockerfile (by tier), gaps, external bases, images on an
  outdated base.
- Tables: per question 1.

Tests, in order:
1. With no lineage directory the section says lineage has not run.
2. The tiles count images, coverage, links by tier, gaps, external bases and outdated bases from the fixture.
3. The table rows (per question 1) name each image by repository path and tag, its parent, its tier and its linked
   Dockerfile locations (repository and path, not blob SHA).
4. An image linked to several Dockerfiles lists them all, newest location first, up to a limit with a count of the
   rest.
5. Golden: the dashboard rendered from the recorded run (slice B) matches a checked-in copy.

## Slice B: fixtures from the JCR run

1. `scripts/record-jcr-fixtures.sh` (run by the owner, as the sandbox cannot reach Docker or JCR) saves JCR's real
   replies for the run: repository list, catalogue, tags, each manifest, each config blob, the storage API's
   last-push. Tokens are never written; timestamps and host names are kept, as they are not secret.
2. `lineage` fixtures: recorded files replace the hand-written ones whose shapes they cover, keeping the names, so
   the existing tests run against real replies. The cases JCR did not produce (manifest list, schema 1, arm-only
   list, errors) stay hand-written; the README says which files are which.
3. History fixtures: the four Buildah configs join `fixtures/history/`; tests that each normalises to the steps of
   its Dockerfile.
4. Contracts: each built image and its Dockerfile become a pair in `testdata/contracts/`, like `python-311`,
   holding facts and lineage to one normal form on real Buildah history.
5. An end-to-end lineage test: a fake Artifactory serving the recorded files, the four Dockerfiles as facts
   fixtures, and the run's result asserted: every parent, every link and its tier, the external base, no gaps.

## Questions

1. **What the lineage tables show.** On the real estate there may be thousands of images, so a full table is too
   long. Proposed: (a) images with no Dockerfile (gaps), (b) images on an outdated base, (c) the most-used internal
   bases with their child counts; each top 20. For a run as small as the demo, all images as a family tree instead.
2. **nginx's four links.** nginx 1.24 linked to 1.20, 1.22 and c8s files too, as the normal form keeps `ENV` keys
   but not values, and those files differ only in values (`NGINX_VERSION`). Keeping values would separate them but
   changes the facts-lineage contract, and values from build arguments would need resolving. Change it now, later,
   or leave it?
3. **The next set of Dockerfiles.** See the options put to the owner.

Answers (6 Nov): 1, the actionable lists, each top 20, with the whole family tree for a run under 50 images;
2, later (recorded in BACKLOG); 3, the `openshift` organisation, through discovery, facts, report, rules and the
converter.

## As built (slice A)

`LineageView` reads lineage's files and names everything: an image by its newest tag (`repository/image:tag`), its
parent by that name, its Dockerfiles as `repository: path`. Under 50 images the dashboard shows the family tree;
from 50 up, the gaps, outdated bases and most-used bases, each top 20. An image lists five Dockerfiles and counts
the rest. The real run's output is the report's `fixtures/lineage-chain`. The golden page (test 5) comes with
slice B's recorded replies.

