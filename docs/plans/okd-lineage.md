# openshift's lineage from published OKD images: plan

Status: planned 7 Nov; questions answered 7 Nov; O1, O2, O4 and O5 built (O3 needed nothing); run 7 Nov; L1 and L2 built; every chosen component now links to its repository's Dockerfile (results below).

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
| Copy images unchanged, digests kept | `lineage-demo.sh` mirrors with `buildah pull/push`, which may recompress | O1, built 7 Nov: a mirror mode, `MIRROR=<file of references>`, with `skopeo copy --preserve-digests` from a container; the list is the CentOS root, the OKD base (by digest, tagged in JCR), `tools` and `network-tools`, and components whose source repository discovery scanned, about 30 |
| Parent by layer prefix | Works on any layers | Squashed images have few layers: a parent is found only if its layers are a prefix, which holds when both were squashed onto the same base. Measured, not assumed |
| Own steps | History after the parent's, steps a build service added dropped (Q13) | O2, built 7 Nov: `OwnSteps.withoutReleaseTooling` cuts the wrapper by position (after the last `ENV __doozer=`, before the `USER 0` that precedes the repository swap, through `rm -rf /tmp/art`); the buildinfo `COPY`s and the final OCI `LABEL` were already dropped |
| Normal form | Reads `#(nop)` and `COPY file:<hash> in <dest>` | O3: nothing to do; it already reads them, as Konflux writes the same |
| Link to a Dockerfile | Step fingerprint against facts' steps | O4, built 7 Nov: a source label (`io.openshift.build.source-location`, or OCI's `org.opencontainers.image.source`) picks the repository; the steps pick the Dockerfile in it and show whether it still matches. Without a label, or when the estate holds none of that repository's Dockerfiles, steps alone, as today (the owner's choice, 7 Nov). In alignment a `LABEL` matches a Dockerfile's whose keys it all holds, as the tooling merges `LABEL`s and adds keys; the real image aligns 5 of 6 with its `Dockerfile.rhel7` |
| Dockerfiles at the image's commit | Discovery read each repository's default branch | O5, built 7 Nov: `scripts/okd-select.sh` writes a release index (repository -> commit) from the chosen images' labels; discovery reruns on those repositories with `RELEASE_INDEX`, as the real estate uses the release database, then facts (the owner's choice, 7 Nov) |

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

## Chosen (7 Nov)

`scripts/okd-select.sh` on `5.0.0-okd-scos.5`: 198 components, 196 from `openshift` repositories, 164 distinct; 26
chosen (`tools`, `network-tools`, and one per repository spread across the release) and 4 bases (`centos:stream10`,
the OKD base `art-okd-images@sha256:5637...`, `oc-mirror`'s base `@sha256:2372...`, and `tools` under its Konflux
name). Picking by name alone gave only `agent-installer-*`, `aws-*` and `azure-*`, hence the spread.

To run, by the owner (the token, Docker and JCR): the three commands in the README's "Trying lineage without an
Artifactory".

## Results of the first run (7 Nov)

Discovery read all 26 chosen repositories at the commits their images name (`RELEASED`), the other 836 at their
default branch; facts parsed 2,585 of 2,646 files (32 templates, 29 failures). The mirror copied 30 images, none
failed; JCR also still held the 5 sclorg images of the first demo (no links here, as their Dockerfiles are not in
this discovery).

| | Result |
| --- | --- |
| Parents | All right: 24 components on the OKD base, `cluster-kube-cluster-api-operator` on the other base, `network-tools` on `tools`; the two bases and `centos:stream10` roots. `tools` and its Konflux name are one image |
| Links | 14 of 26 components linked (weak), each to a Dockerfile in its labelled repository; 12 not linked |
| Outdated bases | 3 |

Why the 12 did not link (several have more than one reason):

| # | Cause | Images |
| --- | --- | --- |
| L1 | A `RUN` after an `ARG` is recorded as `\|1 WORK_DIR=/data /bin/sh -c ...` (the build arguments in force); lineage reads it as `OTHER`, so it matches nothing and the tooling's closing steps go uncut. And `ARG` steps are compared although facts drops them | agent-installer-api-server, aws-karpenter-provider-aws, configmap-reloader, driver-toolkit |
| L2 | The tooling moves every `LABEL` into one at the end; a Dockerfile's `LABEL` at the top, or none, costs a step of order, and with two or three steps 2 of 3 (0.67) is under 0.8 | network-interface-bond-cni, azure-service-operator, kube-storage-version-migrator, gcp-machine-controllers, csi-driver-manila, ibm-cloud-controller-manager, csi-external-snapshotter, cluster-kube-cluster-api-operator, configmap-reloader |

## Fixes

| # | Fix | Module |
| --- | --- | --- |
| L1, built | `HistoryNormaliser` drops the build-argument prefix (`\|N` and its N pairs) before `/bin/sh -c`; `ARG` steps are left out of an image's own steps, as facts leaves them out of a Dockerfile's | `lineage` |
| L2, built | Alignment leaves `LABEL`s out of the ordered steps and counts them as one step: matched when every label key the Dockerfile sets is among the image's; a Dockerfile with no `LABEL` puts no label step in the count, as tooling adds labels to any image (the owner's choice, 7 Nov) | `lineage` |

4. L2: compare `LABEL`s apart from the order of the other steps: the Dockerfile's label keys must all be among the
   image's, and alignment counts the rest?

Answer (7 Nov): 4, yes, labels as a set.

## After L1 and L2 (7 Nov)

Lineage's analysis rerun on the same images (`LineageMain` now runs the analysis alone when `ARTIFACTORY_URL` is
unset): links 14 -> 26 of 26 components, all weak (the tooling's merged `LABEL` keeps any image's steps from equalling
its Dockerfile's), each to the Dockerfile its name suggests (`network-tools` -> `network-tools/Dockerfile`, `tools`
-> `oc/images/tools/Dockerfile`, `agent-installer-api-server` -> `assisted-service/Dockerfile.assisted-service.ocp`);
where a repository holds Dockerfiles with the same steps (`Dockerfile`, `Dockerfile.rhel7`) the link names both. The 8
unlinked images are the three bases and `centos:stream10` (no Dockerfile in this discovery) and the 4 sclorg images
left from the first demo.

Outdated bases (3) follow Q12's rule, a newer push under the same image name whatever the tag, and here that rule
misleads: Konflux keeps every OKD image (bases, `tools`, ...) in one repository, `art-okd-images`, under different
tags, so the OKD base looks replaced by `tools`; and the sclorg leftover on `centos:stream9` counts `stream10` as
newer. The base's own outdated `centos:stream10`, expected above, cannot show: the CentOS image it was built on is not
in JCR, so the base is a root. Left as it is (Q12 is the owner's), noted for when the estate's Artifactory shows how
its tags are used.

