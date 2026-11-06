# More public estates, and openshift's lineage: plan

Status: agreed 6 Nov; openshift discovery running.

sclorg (585 locations) found eighteen problems across discovery, facts, rules and the converter. More estates with
different habits should find more, before the real estate does.

## Estates, in order (owner's choice, 6 Nov)

| Estate | Why | Stages |
| --- | --- | --- |
| `openshift` | Closest to an enterprise RHEL estate: `Dockerfile.rhel`, multi-stage Go builds, `ARG`-driven `FROM`s | all, lineage from published images (below) |
| `konflux-ci` | Red Hat's Buildah build platform: UBI bases, Containerfiles | discovery, facts, report, rules, converter |
| `redhat-developer` | UBI bases, multi-stage Node and Java builds, devfile and IDE images | the same |
| `docker-library` | Debian and Alpine, `apt` and `apk`, heavy shell, generated variants | the same |
| `bitnami` | Thousands of Dockerfiles on one pattern in `containers`: dedupe and near-duplicates at scale | the same |

Each estate: discovery (`scripts/discover.sh <org URL> out/<org>/discovery`, run by the owner, one at a time for
the rate limits), then facts, report, rules and the converter; a table of the numbers beside sclorg's; then a
round of fixes planned the way `demo-fixes.md` was (problem, fix, module, tests in order), test-first.

## openshift's lineage from published images

Building openshift's images would mean compiling large Go projects under amd64 emulation. Instead, published OKD
images (`quay.io/openshift/origin-*`) are copied into the local JCR unchanged, so lineage reads history written by
another build system, not ours, and links images to the Dockerfiles discovery found, which may have moved on since.

- `scripts/lineage-demo.sh` gains a mirror mode: `MIRROR=<file of image references>` copies each image's amd64
  manifest and blobs into `docker-local` (skopeo, which keeps digests) in place of building.
- Questions, answered once discovery's results are in: which release and which images (a base and the images
  built on it whose Dockerfiles discovery found), and how many.

## The Java round trip (after the estates)

Today every Dockerfile goes model -> rendered Dockerfile -> compared; only one test compiles a generated builder
class. Next: for every Dockerfile, emit the class, compile it in memory, run it, render, compare, with its pass rate
beside the existing one. Planned as its own slice.
