# More public estates, and openshift's lineage: plan

Status: openshift, konflux-ci, redhat-developer and docker-library done 6 Nov; bitnami next.

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

## Results so far

| | sclorg | openshift | konflux-ci | redhat-developer | docker-library |
| --- | --- | --- | --- | --- | --- |
| Repositories | 75 | 862 | 158 | 288 | 48 |
| Dockerfile locations | 585 | 3,180 | 3,231 | 604 | 594 |
| Unique files | 579 | 2,833 | 722 | 518 | 581 |
| Parse failures, templates left out | 0% | 2.2% (61) | 0.1% (1) | 1.4% (7; 3 not Dockerfiles) | 0% |
| Unreadable `RUN`s (after S4) | 0% | 0.7% | 1.2% | 1.0% | 0.75% (96 more in PowerShell) |
| Round trip passed | 568 of 568 | 2,730 of 2,833 | 720 of 722 | 502 of 518 (every file that parses) | 576 of 581 |

openshift's fixes are in `openshift-fixes.md`. konflux-ci (3,231 locations, only 722 distinct files: much copying)
needed two: `ARG A=` (an empty default) fails to parse, upstream issue 13, written `ARG A=""` in the retry; and a
backslash kept inside double quotes (`"Red Hat\, Inc."`) had two forms in the model, now one (`\\`). Left: a quoted
`COPY <<'EOF'` heredoc, upstream issue 14 with no safe workaround, and `konflux-ci/mobster`'s parser test input
`tests/data/dockerfiles/sample1` (a `RUN` holding only a `--mount` flag).

redhat-developer needed two: discovery took `dockerfile.svg`, `dockerfile.flex` and `dockerfile.bnf` (an icon and
an IDE plugin's grammar), now skipped with other image and grammar extensions; and two `s2i-dotnetcore` `ENV`
instructions, continued from a bare `ENV \` with comments between the values, lost their names through the
parser's pairs, so the converter now reads `ENV` from its text, as it already did `LABEL`. Left: `ENV KEY = value`
(upstream issue 15, one file), two Kubernetes `CROSS_BUILD_COPY` files and one with an unclosed quote, which Docker
rejects too.

docker-library parsed completely, but 35% of its RUNs were unreadable (loops, `case`, groups, functions), which led
to S4 (`shell-reading.md`). Left: 4 conversions whose rendering does not parse.

The lineage of openshift's published OKD images is still to plan (above).

