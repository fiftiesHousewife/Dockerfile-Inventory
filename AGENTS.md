# Working on this repository

Notes for anyone (human or agent) changing the pipeline. The plan, with its phases and gates, is
[Dockerfile Inventory — Delivery Plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R). What is being worked on and what is queued is in
[BACKLOG.md](BACKLOG.md) (keep it current). The [README](README.md) is for people *running* the
pipeline or reading its output and must stay free of anything below.

## How work proceeds

1. **Plan before each phase, and before each slice of one.** Write `docs/plans/phase-<n>.md`: scope, design,
   the test list in the order the tests will be written, and open questions. Resolve the open questions with
   the project owner before writing code; an assumption that changes the design is a question, not a default.
2. **Test-drive.** One failing test, run and seen to fail for the right reason; the least code to pass;
   refactor with the tests green; repeat. Never write code and its tests in one batch.
3. **Gate.** `./gradlew check` green, then update BACKLOG.md and the plan's status, then commit.

## What this repo is

A Java 25 batch pipeline that inventories every Dockerfile in a GitHub Enterprise estate (about 20,000) at its
last-released commit, parses each unique one with OpenRewrite's `rewrite-docker`, links images to their
bases through Artifactory, and reports archetypes and rule findings. Later phases add fix recipes, a
Dockerfile DSL with a shared renderer, and a Dockerfile-to-DSL converter. It runs as a job in a RHEL pod
on OpenShift.

## Layout

```
build-logic/                included build: the inventory.java convention plugin (toolchain, tests,
                            JaCoCo gate, SpotBugs); every module applies it and nothing else
gradle/libs.versions.toml   the one version catalogue, shared by build-logic and every module
discovery/                  Phase 0: repos -> trees at the released commit -> Dockerfile blobs, once per SHA
                            GitHub (interface) / GitHubHttp: REST trees and orgs, GraphQL repos and blobs
                            RepoScan: one repository; Discovery: the run; DiscoveryOutput: the files
                            run: GHE_API_URL, GHE_TOKEN, OUT_DIR, [RELEASE_INDEX], [CONCURRENCY] ./gradlew :discovery:run
facts/                      Phase 1: blobs -> rewrite-docker -> ImageFacts, normal-form hashes, rates
                            FactsParser, FactExtractor and Steps are the only classes that import org.openrewrite;
                            ShellTokeniser and PackageCalls read RUN bodies; FactsRun: the run
                            Steps: the final image's instructions in lineage's normal form (testdata/contracts/)
                            run: DISCOVERY_DIR, OUT_DIR ./gradlew :facts:run
lineage/                    Phase 1b (in progress): Artifactory images -> family tree -> Dockerfile links
                            Artifactory (interface) / ArtifactoryHttp: REST only, never AQL; Docker v2 catalogue,
                            tags, manifests and blobs, storage API for last push
                            ImageNodes: one tag -> manifest (amd64 entry of a list) -> ImageConfig, or Skipped
                            Lineage: every image's Parent (Root, Of, Ambiguous) from layer prefixes and history
                            HistoryNormaliser: Buildah created_by -> NormalStep (keyword, comparable text, FROM)
```

Modules still to come, one per phase, each added when its phase starts (BACKLOG.md):

```
report/       Phase 2: four-pass clustering, archetype counts, Oracle export
rules/        Phase 3: detect-only recipes, then auto-fixes; depends on facts and renderer
dsl/          Phase 4: instruction model, builders, renderer. Depends on nothing from OpenRewrite
converter/    Phase 5: LST -> model -> generated builder class, round-trip gate
```

## Build and test

```bash
./gradlew check                       # every module: compile with -Werror, tests, JaCoCo gate, SpotBugs
./gradlew :discovery:test             # one module's tests
./gradlew :discovery:test --tests '*DockerfilePathsTest'
```

Inside Claude Code's sandbox use `./gradlew-claude` (gitignored; copy it from clean-logging if missing): the
sandbox only lets traffic out through the proxy in `$https_proxy`, which the JVM ignores, so Gradle fails with
`UnknownHostException` until the proxy and its credentials are passed as system properties.

Compiler warnings fail the build (`-Xlint:all -Werror`), as does a SpotBugs finding at medium
confidence or line coverage under `inventory.minLineCoverage` (80%).

Tests never touch the network. Code that calls GitHub, Artifactory or the release database goes
through an interface with a recorded-response fake in `src/test/resources/fixtures/`, captured from
the real service and trimmed. Dockerfile fixtures are real files from the estate, anonymised, so the
parser sees what production sees.

## How it fits together

**Stages hand files to each other, not objects.** Each phase writes its output under `out/<run-id>/`
and the next reads it, so any stage can be rerun alone and its input inspected by hand:

```
release index (repo -> commit) ─┐
GHE organisations + repos ──────┴─► discovery ─► manifest.jsonl, repos.jsonl, blobs/<sha>
                                                   │
                                     facts ◄───────┘ ─► facts.jsonl, parse-failures.jsonl
Artifactory REST ───────► lineage ─► images.jsonl, lineage.jsonl, links.jsonl, effective.jsonl
                facts + lineage ─► report ─► archetypes.json, dashboard tables ─► Oracle
                facts + lineage ─► rules  ─► findings data tables ─► Oracle; fix PRs (later)
```

**The blob SHA is the identity of a Dockerfile.** Content is fetched, stored and parsed once per blob
SHA; `manifest.jsonl` keeps every location that points at it. Never key anything on a path.

**Scope comes from the release database, not the default branch.** Discovery takes an exported
release index (repository -> released commit SHA). A repository without an entry falls back to its
default branch and is flagged, so the report can say how much of the estate is not release-scoped.

**HTTP first, clone as fallback.** REST trees find paths; GraphQL fetches blob text in batches of
about 50 by alias. A truncated tree falls back to a blobless partial clone. Only `java.net.http` is
used: no git binary is needed in the normal path.

**OpenRewrite stays behind an adapter.** `rewrite-docker` APIs are marked unstable. Only `facts`,
`rules` and `converter` import `org.openrewrite`; `dsl` must never, so the model and renderer stay
usable from tests, Gradle and the converter.

**One renderer.** Fix recipes and DSL builders both produce text through the `dsl` renderer, so
migrated and new Dockerfiles converge on the same lines. A fix never hand-builds a Dockerfile string.

**Per-file rules and effective rules are different things.** Per-file rules run on the LST. Rules
about inherited properties (final user, base approval, arbitrary-UID readiness) run on the effective
image from `lineage`, and each finding names the Dockerfile in the chain that should change.

## Common changes

**Every version lives in `gradle/libs.versions.toml`**, including `rewrite-docker` (through `rewrite-bom`), the
Gradle plugins `build-logic` uses, and the toolchain resolver (which `settings.gradle.kts` reads from the file).
Outside it by necessity: the Gradle version (`gradle-wrapper.properties`), the JDK (`inventory.javaRelease` in
`gradle.properties`) and Kotlin (embedded in Gradle, used by `kotlin-dsl`).

**`check` fails when anything is behind its newest stable release** (`verifyUpToDate`, from the root
`inventory.dependency-updates` plugin): every module's libraries and BOMs, JaCoCo and SpotBugs, build-logic's
plugins, Gradle, and the JDK against the newest LTS. Pre-releases are ignored. A dependency the check cannot
resolve, or a version lookup that fails, fails the build too: a check that cannot look must not pass. To clear
it, bump the version it names and run `./gradlew check`; `rewrite-docker` bumps compare Phase 1 parse-failure
rates before and after. Offline: `./gradlew check -x verifyUpToDate`. Its comparison logic is tested in
`build-logic/src/test`.

**Add a module.** `include("<name>")` in `settings.gradle.kts`, a `build.gradle.kts` applying
`id("inventory.java")`, a row in the layout above, and the phase's entry in BACKLOG.md moved to Active.

**Add a rule.** A rule ID, its tier (detect or auto-fix) and severity in the rule catalogue, a
detect-only recipe writing to the findings data table, tests with one passing and one failing
fixture, and a measured false-positive rate on 50 sampled findings before any auto-fix is enabled.

**Add a renderer for a package manager.** One `PackageInstallRenderer` and its case in
`PackageInstallRenderer.forBase`; golden-file tests per archetype show which archetypes it moves.

## Conventions

Enforced by tools (thresholds in `build-logic/` and `gradle.properties`):

| Standard | Enforced by |
|---|---|
| No compiler warnings | `javac -Xlint:all -Werror` |
| No SpotBugs findings at medium confidence | SpotBugs |
| Line coverage at least 80% per module | JaCoCo |
| Java 25 (newest LTS) language level | Gradle toolchain (`inventory.javaRelease`) |
| UTF-8, LF, 4-space indent, 120-character lines | `.editorconfig`, `.gitattributes` |

And by hand, since no tool checks them:

- Records for data, sealed interfaces for closed sets, `switch` with patterns over `instanceof` chains.
- Small methods with one job; decisions extracted into package-private methods with their own tests.
  Stream and collection operations rather than index loops.
- Tests: no comments inside tests (the method name says what is checked); several checks on one
  result go in one `assertThat(...).satisfies(...)` or `SoftAssertions`; fixtures in
  `src/test/resources/fixtures/`.
- Libraries first: `java.net.http`, Jackson for JSON, OpenRewrite for Dockerfiles. Hand-written code
  only where nothing standard exists (the shell tokeniser, the history normaliser).
- Nothing secret-shaped is ever written out: a finding for a secret-shaped `ENV` or `ARG` stores a
  hash and a location, never the value. This includes logs and test fixtures.
- Tokens come from the environment (`GHE_TOKEN`, `ARTIFACTORY_TOKEN`), never from a file in the repo.
- No abstractions ahead of need, and no error handling for cases that cannot happen; validate at the
  real boundaries (HTTP responses, the release index, Dockerfile text).
- No emojis in source, docs or commits.
- Names say what things are: "Dockerfile" for the source file, "image" for what Artifactory holds,
  "base" for an image others build `FROM`, "archetype" for a cluster from Phase 2.
- Javadoc explains *why*; the code already says what.
