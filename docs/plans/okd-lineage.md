# openshift's lineage from published OKD images: plan

Status: planned 7 Nov; questions answered 7 Nov; O2 and O4 built (O3 needed nothing), O1 and O5 next.

The JCR demo (`lineage-from-jcr.md`) proved lineage on five images we built ourselves with Buildah, from Dockerfiles
we chose. The real estate's images are built by other people's tooling, from Dockerfiles that may have moved on since.
This slice runs lineage on images another build system made: OKD's, published on quay.io, copied unchanged into the
local JCR, and linked to the openshift Dockerfiles discovery found.

## What the published images are (looked at 7 Nov)

- `quay.io/openshift/origin-*` stopped at OKD 4.9 in March 2024; the Dockerfiles discovery read are from November
  2026, so those images would link to almost nothing.
- Current OKD is `quay.io/okd/scos-content`, one repository tagged `<release>-<component>`: release
  `5.0.0-okd-scos.5` (5 Nov 2026, the day before discovery ran) has a tag per component
  (`5.0.0-okd-scos.5-cluster-storage-operator`, ...). Each tag is a single amd64 manifest, not a list.
- They are built by Red Hat's release tooling (ART's doozer) on Konflux, so history carries Buildah's
  `FROM <base>` comments. One image's config (`cluster-storage-operator`, now the fixture
  `lineage/src/test/resources/fixtures/history/okd-cluster-storage-operator.json`):
  - history in Docker's legacy form, `/bin/sh -c #(nop) COPY file:51f5... in /usr/bin/`, 50 entries;
  - squashed: 4 layers, every Dockerfile step marked `empty_layer` except the last;
  - the Dockerfile's steps wrapped by the tooling's: before them `ENV ART_BUILD_*`, `go clean -cache`, `USER 0`,
    `mkdir -p /tmp/art`, a yum repository `COPY`, a certificate download, `ADD ... in /tmp/art`, `ENV __doozer=update`
    and `ENV __doozer=merge`; after them `USER 0`, the repositories swapped back, `rm -rf /tmp/art`, two buildinfo
    `COPY`s and a final OCI `LABEL`;
  - the Dockerfile's `LABEL`s merged into one, with the tooling's keys added (`name`, `vendor`, `version`, `release`,
    ...), so that step cannot match exactly; lineage's alignment scores the rest;
  - labels naming the source: `io.openshift.build.source-location`
    (`https://github.com/openshift/cluster-storage-operator`) and `io.openshift.build.commit.id` (`e7c6430...`).
- The family tree, from layers and `FROM` comments (16 images looked at):
  - root `quay.io/centos/centos:stream10`, public;
  - the OKD base, `quay.io/redhat-user-workloads/ocp-art-tenant/art-okd-images@sha256:5637...` (3 layers, built 21
    Sep from `openshift/images` at `3f1cf08`), public, not in `scos-content`; 14 of the 16 are built on it;
  - `network-tools` is built on `tools` (a second level); `oc-mirror` on another base;
  - today's `centos:stream10` (4 Nov) is not the one the base was built on, so the base should show as built on an
    outdated base: one of the gaps lineage exists to find.

## What lineage needs, and what already works

| Need | Today | Change |
| --- | --- | --- |
| Copy images unchanged, digests kept | `lineage-demo.sh` mirrors with `buildah pull/push`, which may recompress | O1: a mirror mode, `MIRROR=<file of references>`, with `skopeo copy --preserve-digests` from a container; the list is the CentOS root, the OKD base (by digest, tagged in JCR), `tools` and `network-tools`, and components whose source repository discovery scanned, about 30 |
| Parent by layer prefix | Works on any layers | Squashed images have few layers: a parent is found only if its layers are a prefix, which holds when both were squashed onto the same base. Measured, not assumed |
| Own steps | History after the parent's, steps a build service added dropped (Q13) | O2, built 7 Nov: `OwnSteps.withoutReleaseTooling` cuts the wrapper by position (after the last `ENV __doozer=`, before the `USER 0` that precedes the repository swap, through `rm -rf /tmp/art`); the buildinfo `COPY`s and the final OCI `LABEL` were already dropped |
| Normal form | Reads `#(nop)` and `COPY file:<hash> in <dest>` | O3: nothing to do; it already reads them, as Konflux writes the same |
| Link to a Dockerfile | Step fingerprint against facts' steps | O4, built 7 Nov: a source label (`io.openshift.build.source-location`, or OCI's `org.opencontainers.image.source`) picks the repository; the steps pick the Dockerfile in it and show whether it still matches. Without a label, or when the estate holds none of that repository's Dockerfiles, steps alone, as today (the owner's choice, 7 Nov). In alignment a `LABEL` matches a Dockerfile's whose keys it all holds, as the tooling merges `LABEL`s and adds keys; the real image aligns 5 of 6 with its `Dockerfile.rhel7` |
| Dockerfiles at the image's commit | Discovery read each repository's default branch | O5: a script writes a release index (repository -> commit) from the chosen images' labels; discovery reruns on those repositories with `RELEASE_INDEX`, as the real estate uses the release database, then facts (the owner's choice, 7 Nov) |

## Questions

1. Linking: the labels name each image's repository and commit. Use them to pick the repository (and check the
   steps against that repository's Dockerfiles), or link by steps alone as today, so the test is the one the real
   estate faces?
2. Commits: build a release index from the images' labels (repository -> commit) and rerun discovery for those
   repositories with it, so Dockerfiles and images match; or compare against the default-branch Dockerfiles discovery
   already has, and report how many drifted?
3. Scope: how many images from release `5.0.0-okd-scos.5` (198 components): about 30, or the whole release?

Answers (7 Nov): 1, labels pick the repository, steps confirm; 2, rediscover at the images' commits; 3, about 30
images: the shared bases and components whose source repository discovery scanned.

## Tests, in order

1. Built: the OKD fixture's own steps are the Dockerfile's (`COPY`, `COPY`, `COPY`, `ENTRYPOINT`, the merged
   `LABEL`), the wrapper cut; a Dockerfile's own `USER 0` and what follows the wrapper are kept.
2. Built: an image whose source label names a repository links only among that repository's Dockerfiles, whatever
   base they name, even when another repository's Dockerfile has the same steps; none aligning is no link; a
   repository the estate does not hold links as before; the real OKD image links (weak) to its `Dockerfile.rhel7`.
3. Recorded fixtures (manifest and config, trimmed) of two real images, a base and one built on it, give the right
   parent and own steps.
4. The demo on the chosen images: parents, links and gaps reported, with a table of what linked and why the rest did
   not.
