# Continuous integration: plan

Status: done 10 Nov (asked 10 Nov: "We maintain it here, and publish libraries and a cli ... CI first then
publication"). The first run failed on a SpotBugs finding (a nullable `Path.getFileName()` in the new build retry)
that local runs had missed because the session's Gradle wrapper script exited 0 whatever Gradle did; fixed, and the
second run green in about four minutes.

## Why

Nothing checks a push: `./gradlew check` runs only on the machine of whoever pushes, and the repository has no
`.github/workflows`. Publication to Maven Central comes next and needs a build that is checked the same way every time
before anything is released.

## Design

- `.github/workflows/check.yml`: on every push and pull request, `./gradlew check` on `ubuntu-latest`, Gradle set up
  by `gradle/actions/setup-gradle` (its cache keyed on the wrapper and the version catalogue). One JDK to run Gradle;
  the toolchains (25, and 21 for the published libraries' tests) come from Gradle's foojay resolver, as they do
  locally. Test reports and the JaCoCo and SpotBugs reports kept as the run's artifacts when it fails.
- The tagged bulk checks (`roundTrip`, `imageRoundTrip`, `validate`) are left out: they need an estate on disk (and
  Docker), and skip without one.
- The actions pinned by commit SHA, as the project asks of base images (R1), with Dependabot keeping them current.
- `verifyUpToDate` in its own workflow, weekly (Mondays 06:00 UTC) and on demand.
- The `Containerfile`, `.containerignore` and `scripts/inventory` (the image's entry point), with their plan item (corporate-readiness C11, the pod) removed: the pipeline is consumed as
  published artifacts, not run as a pod.

## Tests, in order

A workflow is checked by running it: the first push of the workflow, green; then a branch with a failing test, red,
with the report kept.

## Questions answered

1. (10 Nov: "sure by all means": split.) `verifyUpToDate` (part of `check`) fails when any dependency has a newer release. On every push, that turns CI red
   the day an upstream library releases, whatever the push changed. Run `check -x verifyUpToDate` on pushes and pull
   requests, and `verifyUpToDate` alone on a weekly schedule; or keep it in every push's `check`?
