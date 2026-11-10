# A faster image round trip, every sample built: plan

Status: planned and built 10 Nov; the full ten with charts-syncer to be run by the owner (asked 10 Nov: "19 mins is so slow ... lets actually measure also? and can we make sure all
samples build").

## Why

`./gradlew imageRoundTrip` took 18 minutes 45 on the owner's machine (10 Nov, Docker Desktop with 8 CPUs and 12 GB,
the default ten). The test's own work between builds is about 20 seconds; the rest is Buildah, one build at a time:

| Dockerfile | Builds | Time |
| --- | --- | --- |
| docker-library/php 8.4 alpine fpm | 3, each compiling PHP | 7 min 42 |
| redhat-developer/alizer, Quarkus | 5 (three tries at the original's context) | 3 min 15 |
| openshift/aws-load-balancer-operator | 3 | 2 min 16 |
| bitnami notation | 1, not built | 1 min 12 |
| the other six | | 4 min 38 |

(From the build logs' last-write times; JFR would show the test JVM waiting on its child processes, as on 8 Nov.)

One sample never builds: bitnami's `RUN --mount=type=secret,id=...,env=...` is refused by Buildah 1.43.5 ("secret
should have syntax id=id[,target=path,...]"), checked on a two-line Dockerfile. On 8 Nov the Debian mirror's hash-sum
mismatch failed the step before it and hid this; on 10 Nov the mirror served it and the build reached the mount.

## Design

1. **Measured, not estimated.** Each result records its builds' seconds (`seconds`: original, regenerated, control)
   and the run prints its total, so before and after are compared from the output.
2. **Several Dockerfiles at once.** `IMAGE_ROUND_TRIP_PARALLEL` (default 4, refused when not a positive number),
   the Dockerfiles on that many threads in one Buildah container, the results in the chosen order. Clearing every
   container and dangling image (`buildah rm --all`, `rmi --prune`) moves from each pair's end to the run's end, so it
   cannot kill another pair's build; each pair still removes its own three tags.
3. **The regenerated and control builds together**, once the original has built: neither reads the other.
4. **Every sample must build.** The test fails when a chosen Dockerfile is not built, naming it and why; a sample
   that cannot build on any builder is replaced, not tolerated.

## Tests, in order

1. `BuildahContainer`: the parallel setting read, defaulted and refused.
2. `ImageComparisonRun`: a result's build seconds (a fake builder with a clock); the regenerated and control builds
   started together (a fake builder recording overlap); clearing not run per pair.
3. The comparison over several Dockerfiles: results in the chosen order whatever finishes first.
4. `ImageRoundTripTest`: fails when a pair is not built.
5. Measured: the default ten before (18:45, above) and after, on the owner's machine.

## Questions answered

1. Bitnami's sample (10 Nov: "find a sample that works with buildah"): `bitnami/charts-syncer`'s `Dockerfile`
   (minideb, `install_packages`, a bash `SHELL`, `ARG TARGETARCH`, `USER 1001`, its prebuilt binary onto scratch),
   built on Buildah 1.43.5 with the binary stubbed in 54 seconds. The other bitnami candidates without `env=` are Go
   builders on `bitnami/golang` and minideb test images.

## Built and measured (10 Nov)

`ImageComparisons` runs the Dockerfiles `IMAGE_ROUND_TRIP_PARALLEL` at once, those sharing a checkout one after
another, and times each; `ImageComparisonRun` builds the regenerated and control images together; clearing moved to
`BuildahContainer.close`; the test fails on any Dockerfile not built. Per-build seconds were not added: each result's
seconds and the run's total answered the question, and the logs' write times give the builds.

The nine samples with checkouts (charts-syncer's needs a clone this session's sandbox cannot write), the same machine:

| Dockerfile | Before (one at a time) | After (4 at once) |
| --- | --- | --- |
| docker-library php, compiling PHP | 462s | 673s |
| alizer, Quarkus | 195s | 371s |
| external-secrets | 77s | 247s |
| aws-load-balancer-operator | 136s | 227s |
| the other five | 183s | 493s |
| the run | 1,053s | 674s |

36% faster, not the 60% estimated: each build slows when four share the VM's 8 CPUs, and php's two `make -j 8`
builds at once set the run's length. All nine the same, as before.

