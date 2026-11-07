# openshift's lineage from published OKD images: plan

Status: planned 7 Nov; questions open.

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
- They are built by Red Hat's release tooling (doozer, with imagebuilder), and it shows in one image's config
  (`cluster-storage-operator`):
  - history in Docker's legacy form, `/bin/sh -c #(nop) COPY file:51f5... in /usr/bin/`, 50 entries;
  - squashed: 4 layers, every Dockerfile step marked `empty_layer` except the last;
  - steps doozer adds around the Dockerfile's own: `ADD ... in /tmp/art`, `ENV __doozer=update BUILD_RELEASE=...`,
    `ENV __doozer=merge OS_GIT_COMMIT=...`, a yum repository swap, two buildinfo `COPY`s, OCI labels;
  - labels naming the source: `io.openshift.build.source-location`
    (`https://github.com/openshift/cluster-storage-operator`) and `io.openshift.build.commit.id` (`e7c6430...`).

## What lineage needs, and what already works

| Need | Today | Change |
| --- | --- | --- |
| Copy images unchanged, digests kept | `lineage-demo.sh` mirrors with `buildah pull/push`, which may recompress | O1: a mirror mode, `MIRROR=<file of references>`, with `skopeo copy --preserve-digests` from a container |
| Parent by layer prefix | Works on any layers | Squashed images have few layers: a parent is found only if its layers are a prefix, which holds when both were squashed onto the same base. Measured, not assumed |
| Own steps | History after the parent's, steps a build service added dropped (Q13) | O2: doozer's steps recognised as builder-added (`ENV __doozer=`, `ADD ... in /tmp/art`, the `/tmp/art` and `art-*` repo cleanup, buildinfo `COPY`s) |
| Normal form | Reads `#(nop)` and Buildah's `COPY` | O3: `COPY file:<hash> in <dest>` and `COPY dir:<hash> in <dest>` read as a `COPY` to `<dest>`, which is all facts keeps of a `COPY` |
| Link to a Dockerfile | Step fingerprint against facts' steps | O4: depends on question 1 |
| Dockerfiles at the image's commit | Discovery read each repository's default branch | O5: depends on question 2 |

## Questions

1. Linking: the labels name each image's repository and commit. Use them to pick the repository (and check the
   steps against that repository's Dockerfiles), or link by steps alone as today, so the test is the one the real
   estate faces?
2. Commits: build a release index from the images' labels (repository -> commit) and rerun discovery for those
   repositories with it, so Dockerfiles and images match; or compare against the default-branch Dockerfiles discovery
   already has, and report how many drifted?
3. Scope: how many images from release `5.0.0-okd-scos.5`: about 30 (their bases, and components whose source
   repository discovery scanned), or every component in the release (about 200, several GB)?

## Tests, in order (O2, O3; the rest after the questions)

1. `#(nop) COPY file:51f5... in /usr/bin/` normalises to `COPY /usr/bin/`; `COPY dir:... in /manifests` to
   `COPY /manifests`.
2. Each doozer step is builder-added; the Dockerfile's own `ENTRYPOINT`, `USER` and `LABEL` are not.
3. Recorded fixtures (manifest and config, trimmed) of two real images, a base and one built on it, give the right
   parent and own steps.
4. The demo on the chosen images: parents, links and gaps reported, with a table of what linked and why the rest did
   not.
