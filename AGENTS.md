# Contributing

Notes for anyone, human or agent, changing the pipeline. Start with [the overview](docs/overview.md): the problem,
the approach and why each stage exists. The plan, with its phases and gates, is the
[delivery plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R); what is being worked on and what is queued is in
[BACKLOG.md](BACKLOG.md), which you keep current. The [README](README.md) is for people running the pipeline or
reading its output: settings, outputs, what the numbers mean. Nothing about the code goes there.

## How work proceeds

1. **Plan before each phase, and before each slice of one.** Write `docs/plans/<name>.md`: why, the design, the
   tests in the order they will be written, and open questions. Resolve the questions with the project owner
   before writing code, one at a time; an assumption that changes the design is a question, not a default.
2. **Test-drive.** One failing test, run and seen to fail for the right reason; the least code to pass; refactor
   with the tests green; repeat. Never write code and its tests in one batch.
3. **Gate.** `./gradlew check` green, then BACKLOG.md and the plan's status updated, then commit and push.
4. **Measure on real Dockerfiles.** A change to parsing, lineage or the converter is run on the public estates
   before and after (`docs/plans/more-estates.md` lists them); a change meant to keep behaviour compares the outputs
   byte for byte.

## Layout

```
build-logic/        included build: the inventory.java convention plugin (toolchain, -Werror, tests, JaCoCo gate,
                    SpotBugs) and inventory.dependency-updates (verifyUpToDate); every module applies inventory.java
gradle/             libs.versions.toml, the one version catalogue
http/               corporate networks, shared by discovery and lineage: HttpSettings (every setting from the
                    environment, with defaults; the HttpClient), ProxySettings (HTTPS_PROXY, HTTP_PROXY, NO_PROXY),
                    TrustBundle (a corporate CA), RetryPolicy (backoff, rate-limit waits, RateLimitWaitTooLong),
                    TokenSource (NAME or NAME_FILE, re-read when the file changes)
discovery/          stage 0: organisations -> repositories -> trees at the released commit -> Dockerfile blobs
                    Discovery (the run), Scans (repositories on virtual threads), RepoScan (one repository),
                    TreeWalk (truncated trees by subtree), BlobFetch (GraphQL batches of 50), DiscoveryOutput (files)
                    GitHub (interface) / GitHubHttp, GitHubRequests (sending, errors), GraphQlRepositories,
                    GraphQlBlobs, RestTrees; GitHubApi (the three URL forms); RateLimit; ReleaseIndex
                    DockerfilePaths: what counts as a Dockerfile (not source, templates, images, grammars, symlinks,
                    vendor/ or test inputs)
facts/              stage 1: blobs -> rewrite-docker -> ImageFacts, steps, normal-form hashes, rates
                    FactsParser (parse, retry, verdict), RetryText (each parser workaround), Templates, MergedLines
                    FactExtractor (entry), StageScan, StageEnd, RunTally, RunScripts, BaseImages, BuildKitFeatures,
                    ArgumentText; Steps (the final image's steps, lineage's normal form)
                    ShellTokeniser (entry): ShellWords, ShellScan, ShellQuotes, ShellSubstitutions, ShellToken
                    (characters to words), ShellCommands, ShellCommand, ShellKeywords (words to commands)
                    PackageCalls; shared text: Continuations, KeyValuePairs, Expansions; NormalForms; FactsRun
lineage/            stage 1b: Artifactory images -> family tree -> links to Dockerfiles -> effective images, gaps
                    Artifactory (interface) / ArtifactoryHttp (endpoints, REST only, never AQL), ArtifactoryClient
                    (authorised GET, retries); Inventory -> images.jsonl, configs/; ImageNodes, ImageConfig
                    Lineage (parents from layer prefixes), HistoryNormaliser and PairKeys (history -> NormalStep),
                    OwnSteps (an image's own steps; Konflux's and Red Hat release tooling's steps cut)
                    Links (the run), Alignment (scoring), BasePaths, SourceLabels (a label limits the repository)
                    Fold (effective user, env, labels, with who set each), Gaps, Analysis, Dockerfiles, LineageRates
report/             stage 2: facts + manifest (+ lineage) -> passes, archetypes, bases, tables, dashboard
                    Estate, EffectiveUsers, Passes, MinHash, NearDuplicates, Archetypes, BaseCatalogue, EolList,
                    Summary, Tables, Table, ReportRun
                    Dashboard (assembly): HeadlineSection, ArchetypeSection, BasesSection, BreakdownSection,
                    LineageSection, LineageTree; CoverageChart, BasesChart, StackedChart, Svg; ArchetypeCards,
                    ArchetypeSentence, HiddenSecrets; Health (thresholds), About (info text), Style, Html
rules/              stage 3: detect-only rules over facts, parsed Dockerfiles and lineage
                    Rule, EffectiveRule, Finding, Subject, ImageView, RuleRun (a failing rule is a finding);
                    one class per rule (S1-S4, R1-R3, H1-H3, I1, I2); RuleSet, RulesRun, DiscoveryFiles,
                    FindingsTable, RuleRates, Samples; renovate/ holds the Renovate preset
dsl/                stage 4: the Dockerfile model and the one renderer; no OpenRewrite
                    Dockerfile, Stage, Instruction, RunBody, BaseImage, PackageInstall, PackageManager;
                    DockerfileRenderer (Target), DockerQuotes, RunCommands, PackageInstallRenderer
                    ImageBuilder (the fluent API) over InstructionBuilder, Draft, FinalStage, RunMerge;
                    Policy, PolicyCheck, UncleanedInstalls, PolicyViolation; Secrets (shared with rules and report)
converter/          stage 5: Dockerfile -> model -> Dockerfile, and Java builder classes
                    Convert (dispatch), ConvertPairs, ConvertCopy, ConvertCommands, Printed, Values, Pairs, Installs;
                    RoundTrip, CanonicalSteps; JavaEmitter, ClassNames, GeneratedClasses; ConverterRun, Coverage,
                    RunFiles
testdata/contracts/ the normal-form contracts between facts and lineage: real Dockerfiles against the history of
                    the real images they built (python-311, sclorg-c9s-*)
testdata/corpus/    the fixture bank: real Dockerfiles and images that broke something, each with its expected
                    outcome; facts', the converter's and lineage's corpus tests ratchet on it (its README)
scripts/            discover.sh (one organisation with the GitHub CLI's token), combine.sh (several discovery
                    outputs as one estate), lineage-demo.sh (a local JCR: Buildah builds, or MIRROR= published
                    images), okd-select.sh (OKD images and their release index), corpus-collect.sh and
                    corpus-collect-images.sh (the fixture bank from a run), jcr-diagnose.sh, record-jcr-fixtures.sh
docs/               overview.md (the problem and the approach), plans/ (one per phase or slice), upstream/
                    (rewrite-docker issues)
```

Each module's run entry point is its `...Main`; the README lists their settings.

## Build and test

```bash
./gradlew check                                   # compile with -Werror, tests, JaCoCo, SpotBugs, verifyUpToDate
./gradlew check -x verifyUpToDate                 # offline
./gradlew :facts:test --tests '*ShellWordsTest'   # one class
```

Inside Claude Code's sandbox use `./gradlew-claude` (gitignored; copy it from another checkout if missing): the
sandbox lets traffic out only through the proxy in `$https_proxy`, which the JVM ignores until the proxy is passed
as system properties. The `http` module fixes this for the pipeline itself; Gradle still needs it.

Tests never touch the network. GitHub and Artifactory go through an interface, with recorded responses served from
`src/test/resources/fixtures/` (by a fake or a loopback server). Dockerfile fixtures are real files, trimmed, so the
parser sees what production sees; every fixture folder has a README saying where each file came from.

## How it fits together

**Stages hand files to each other, not objects.** Each stage writes its output under `out/<run>/` and the next
reads it, so any stage can be rerun alone and its input inspected by hand:

```
release index (repo -> commit) ─┐
GHE organisations + repos ──────┴─► discovery ─► manifest.jsonl, repos.jsonl, blobs/<sha>
                                                   │
                                     facts ◄───────┘ ─► facts.jsonl, parse-failures.jsonl, rates.json
Artifactory REST ───────► lineage ─► images.jsonl, lineage.jsonl, links.jsonl, effective.jsonl, gaps
                facts + lineage ─► report ─► archetypes.json, tables/*.csv, dashboard.html ─► Oracle
                facts + lineage ─► rules  ─► findings.jsonl, tables, samples ─► Oracle; fixes later
                facts           ─► converter ─► roundtrip.jsonl, coverage.json, builder classes
```

**The blob SHA is a Dockerfile's identity.** Content is fetched, stored and parsed once per blob SHA;
`manifest.jsonl` keeps every location that points at it. Never key anything on a path.

**Scope comes from the release database, not the default branch.** A repository without a release-index entry
falls back to its default branch and is flagged, so the report can say how much is not release-scoped.

**OpenRewrite stays behind an adapter.** Only `facts`, `rules` and `converter` import `org.openrewrite`; `dsl`
never does. The parser is being forked into this repository (`docs/plans/parser-fork.md`): upstream's last open
release is 8.90.4.

**One renderer.** Fixes and builders produce text through `dsl`'s renderer, so migrated and new Dockerfiles
converge on the same lines. Nothing hand-builds a Dockerfile string.

**Per-file rules and effective rules are different things.** Per-file rules read one Dockerfile. Rules about what
an image inherits (final user, base approval, arbitrary-UID readiness) read the effective image from `lineage`, and
each finding names the Dockerfile in the chain that should change.

## Common changes

**Versions.** Every version lives in `gradle/libs.versions.toml`, except Gradle (`gradle-wrapper.properties`), the
JDK (`inventory.javaRelease` in `gradle.properties`) and Kotlin (Gradle's). `check` fails when anything is behind
its newest stable release; bump what it names and run `./gradlew check`. A parser change compares parse-failure
rates before and after.

**Add a module.** `include("<name>")` in `settings.gradle.kts`, a `build.gradle.kts` applying `id("inventory.java")`,
a row in the layout above, an entry in BACKLOG.md.

**Add a rule.** An ID, tier (detect or fix) and severity; a class implementing `Rule` or `EffectiveRule`; tests with
a passing and a failing fixture; a measured false-positive rate on 50 sampled findings before any fix is enabled.

**A case that breaks something.** Add it to the fixture bank (`testdata/corpus/`) with its outcome; the collectors
do this from a run. When a change turns cases, the corpus tests name them: update their `expected.json` in the same
commit, so the change's effect is in the diff.

**A Dockerfile the parser gets wrong.** Shrink the real lines while the error stays the same; record the minimal
reproduction in `docs/upstream/rewrite-docker-issues.md` and the trimmed lines as a fixture. Until the fork is in,
a safe rewrite (it keeps every line number and the meaning) goes in `RetryText` as its own method; after it, the fix
goes in the parser.

**A new setting.** Read it in the stage's `...Main` from the environment map, give it a default, refuse a bad value
naming the variable, list it in the README. Network settings belong in `http`'s `HttpSettings`.

**Files shared between modules** (`testdata/contracts/`, dsl's goldens) are declared as test inputs in each reading
module's `build.gradle.kts`, so a change reruns the tests that read them.

## Conventions

Enforced by tools:

| Standard | Enforced by |
| --- | --- |
| No compiler warnings | `javac -Xlint:all -Werror` |
| No SpotBugs findings at medium confidence | SpotBugs |
| Line coverage at least 80% per module | JaCoCo |
| Java 25 (newest LTS) | Gradle toolchain (`inventory.javaRelease`) |
| UTF-8, LF, 4-space indent, 120-character lines | `.editorconfig`, `.gitattributes` |

By hand, since no tool checks them:

- **Visibility.** A type is public only when another module imports it, it is a run entry point, or a public API
  type exposes it. Everything else is package-private.
- **Comments.** Javadoc only on public API types and their public members: a sentence or two on why. Elsewhere, a
  `//` line only where the code cannot say why and the reason would be lost: an upstream bug's number, an external
  format's quirk. No decision history, no "the owner's choice"; the plans hold decisions.
- **Size.** A production class holds one job and stays under about 150 lines; a method under about 25.
- **Methods.** Any method with a decision or computation is package-private with its own direct test; `private`
  only for one-line helpers without a branch.
- **Tests.** One test class per class it tests directly; the method name says what is checked; no comments inside
  tests; several checks on one result in `satisfies` or `SoftAssertions`; fixtures in `src/test/resources/fixtures/`.
- **Java.** Records for data, sealed interfaces for closed sets, `switch` with patterns, streams over index loops
  (character scanners excepted).
- **Libraries first.** `java.net.http`, Jackson, OpenRewrite; hand-written code only where nothing standard fits
  (the shell reader, the history normaliser).
- **Secrets.** Nothing secret-shaped is ever written out, in output, logs or fixtures: a finding stores a hash and a
  location. Tokens come from the environment or a mounted file, never from the repository.
- **Validation.** At the real boundaries only (HTTP responses, the release index, Dockerfile text, settings); no
  handling for cases that cannot happen, no abstraction ahead of need.
- **Words.** "Dockerfile" for the source file, "image" for what a registry holds, "base" for an image others build
  `FROM`, "archetype" for a family of near-identical Dockerfiles, "estate" for the Dockerfiles in scope.
- No emojis in source, docs or commits.
