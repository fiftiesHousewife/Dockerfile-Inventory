# Contributing

Notes for anyone, human or agent, changing the pipeline. Start with [the overview](docs/overview.md): the problem,
the approach and why each stage exists. The plan, with its phases and gates, is the
[delivery plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R); what is being worked on and what is queued is in
[BACKLOG.md](BACKLOG.md), which you keep current. The [README](README.md) is a short read for people running the pipeline: what it
does, a quick start, examples, the commands; the detail (each stage's settings, outputs and what the numbers mean) is
in [docs/guide/](docs/guide/), one guide per stage, linked from it. Neither holds history or anything about the code.

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
                    SpotBugs), inventory.vendored (the same for copied code, without -Werror and SpotBugs, generated
                    code out of coverage), inventory.published (a Maven Central bundle, built and verified in check)
                    and inventory.dependency-updates (verifyUpToDate); every module applies inventory.java, except
                    parser, which applies inventory.vendored; parser, dsl and reader also apply inventory.published
                    (built for and tested on Java 21)
gradle/             libs.versions.toml, the one version catalogue
parser/             the Dockerfile parser: a fork of OpenRewrite's rewrite-docker 8.90.4 (Apache 2.0), packages
                    io.github.fiftieshousewife.dockerfile.parser (.tree, .internal, .internal.grammar), published
                    on its own as io.github.fiftieshousewife:dockerfile-parser; DockerParser, Docker (the tree),
                    DockerVisitor, DockerIsoVisitor, DockerPrinter; the grammar in src/main/antlr, its generated
                    lexer and parser kept in the repository (./gradlew :parser:generateAntlrSources); LICENSE,
                    NOTICE, and a note in each changed file's header
http/               corporate networks, shared by discovery and lineage: HttpSettings (every setting from the
                    environment, with defaults; the HttpClient), ProxySettings (HTTPS_PROXY, HTTP_PROXY, NO_PROXY),
                    TrustBundle (a corporate CA), RetryPolicy (backoff, rate-limit waits, RateLimitWaitTooLong),
                    TokenSource (NAME or NAME_FILE, re-read when the file changes), Throttle (a run's
                    calls a second, slowing with the server), HttpLog (the debug lines)
discovery/          stage 0: organisations -> repositories -> trees at the released commit -> Dockerfile blobs
                    Discovery (the run), Scans (repositories on virtual threads), RepoScan (one repository),
                    TreeWalk (truncated trees by subtree), BlobFetch (GraphQL batches of 50), DiscoveryOutput (files)
                    ChangeFetch and GraphQlHistory: each location's last change, in batches of 50
                    GitHub (interface) / GitHubHttp, GitHubRequests (sending, errors), GraphQlRepositories,
                    GraphQlBlobs, RestTrees; GitHubApi (the three URL forms); RateLimit; ReleaseIndex
                    DockerfilePaths: what counts as a Dockerfile (not source, templates, images, grammars, symlinks,
                    vendor/ or test inputs)
facts/              stage 1: blobs -> rewrite-docker -> ImageFacts, steps, normal-form hashes, rates
                    FactsParser (parse, verdict), Templates
                    FactExtractor (entry), StageScan, StageEnd, RunTally, RunScripts, BaseImages, BuildKitFeatures;
                    Steps (the final image's steps, lineage's normal form); FinalConfig (ImageConfig: the
                    final image's configuration, secrets hidden) and StageGraph (ImageStage: per stage its counts,
                    StageCopies' CopyFacts and StagePermissions' PermissionChanges), for the catalogue
                    PackageCalls; NormalForms; FactsRun; Workloads (workload YAML: Workload, its pod and containers);
                    the shell reader and shared text helpers come from reader
lineage/            stage 1b: Artifactory images -> family tree -> links to Dockerfiles -> effective images, gaps
                    Artifactory (interface) / ArtifactoryHttp (endpoints, REST only, never AQL), ArtifactoryClient
                    (authorised GET, retries); Inventory -> images.jsonl, configs/; ImageNodes, ImageConfig
                    Lineage (parents from layer prefixes), HistoryNormaliser and PairKeys (history -> NormalStep),
                    OwnSteps (an image's own steps; Konflux's and Red Hat release tooling's steps cut)
                    Links (the run), Alignment (scoring: the best and the runner-up), BasePaths, StepIndex (candidates
                    by rare steps), LinkRule (which links credit an image to a Dockerfile: `credited` on each)
                    Fold (effective user, env, labels, with who set each), Gaps, Analysis, Dockerfiles, LineageRates
report/             stage 2: facts + manifest (+ lineage) -> passes, archetypes, the catalogue, HTML pages from it
                    Estate, EffectiveUsers, Passes, MinHash, NearDuplicates, Archetypes, Tallies, ReportRun
                    CatalogueRun (catalogue/dockerfiles.jsonl): CatalogueEntry, CatalogueEntries, CatalogueLinks,
                    Footprint, Runtimes, Versions; RegistryImages (lineage's output per image), Unmaintained,
                    Hierarchies (chains, layers, blockedBy), CatalogueSettings (FootprintRules),
                    Placement; Actions: ReleaseScope, Reaches, Conversions, Siblings, BaseLags, Cadences, Provenance
                    CatalogueMain (the catalogue alone, for the validator: the report distribution's `bin/catalogue`)
                    Pages (writes them, from CatalogueView and ArchetypeRows): OverviewPage (EstateFigures),
                    HierarchyPage, BlockedPage, DockerfilePage (DockerfileSections), ArchetypeTable, ArchetypeName,
                    ActionPlanPage (ActionPlanView: the validator's output), WorkloadTable, Deployments (a Dockerfile's workloads);
                    Page, Html, Style, About (info text)
rules/              stage 3: detect-only rules over facts, parsed Dockerfiles and lineage
                    Rule, EffectiveRule, CatalogueRule (over the estate, from CatalogueView), Finding, Subject,
                    ImageView, RuleRun (a failing rule is a finding); one class per rule (S1-S7, R1-R4, H1-H9, I1, I2;
                    AddSources reads an ADD or COPY); RuleSet, RulesRun, DiscoveryFiles, RuleRates, Samples;
                    renovate/ holds the Renovate preset
validator/          stage 3c: facts -> each image judged through a platform profile -> actions
                    Profile (profiles/*.json: severities per check, allowed registries), Check, Severity, Certainty;
                    Subject (a facts line as the checks read it, with the EffectiveUser that EffectiveUsers reads
                    from lineage's credited links), PlatformCheck: FinalUser (P1), WritablePaths,
                    LowPorts, UserSwitch, SetuidBits, ShellStart, BaseRegistry, UnpinnedBase, DeploymentHints,
                    UserIdentity (P10); Validation, Finding, Verdict; Actions, Action, Location; Fixes, Fix (an action
                    made once: Catalogue, the catalogue stage's archetypes and links; Inheritance, per check);
                    RuleFindings (the rules' findings.jsonl as findings); JsonLines; ValidatorRun, ActionPlan
                    Workload, PodCheck: Privileged (D1) to Unconfined (D6), FloatingImage (D7); PodValidation,
                    WorkloadVerdict; ImageReference, ImageLinks, ContainerLink, ContainerImage (a container's image
                    linked through lineage, P9 answered by the manifest); FloatingBase (P11)
cli/                the docker-inventory command (`DockerInventory`): Commands (each command and the settings it reads,
                    required ones checked before it runs), Command, Setting, Help, NearMisses (a setting's typo), Flags (--settings, --load), SettingsFile (the YAML),
                    Load (the gentle and normal presets), Logging,
                    Version (from the build); the `docker-inventory` distribution (`installDist`), tested as installed
survey/             one command for a survey: SurveyMain (`survey [--try] <URL>`), Survey (the stages in order, each
                    with the folders the earlier ones wrote); every stage's ...Main.run(env)
tools/              the contributors' tools, not in the image: CorpusCollect (`./gradlew collectCorpus
                    --args=<run>`: the Dockerfiles a run found hard into testdata/corpus/dockerfiles; RunOutcomes,
                    Workaround, CorpusCase), ImageCollect (`./gradlew collectImages --args=<run>`: a lineage run's
                    images into testdata/corpus/images); OkdSelect (`./gradlew selectOkd`: an OKD release's images for a
                    lineage run, from quay.io: OkdChoice, Quay, QuayHttp), JcrRecord (`./gradlew recordJcrFixtures`:
                    a local JCR's replies as lineage's fixtures), LineageDemo (`./gradlew lineageDemo`: lineage end
                    to end on a local JCR in Docker: BuildPlan, JcrContainers, JcrApi, DemoImages, Commands),
                    JcrDiagnose (`./gradlew jcrDiagnose`); Json (the bank's JSON, laid out as jq
                    lays it out)
secrets/            Secrets: what counts as a secret value or name, shared by facts, rules and report; not published
dsl/                stage 4: the Dockerfile model and the one renderer; no OpenRewrite; package
                    io.github.fiftieshousewife.dockerfile.model, to be published as dockerfile-model
                    Dockerfile, Stage, Instruction (sealed) and a record per instruction, one file each (Arg, Env,
                    Label, Run, Copy, Add, CopyHeredoc, ... Raw; Copy, Add and CopyHeredoc with of/to and with-methods),
                    RunBody, RunFlag, Port, CommandForm, BaseImage, PackageInstall, PackageManager, OrderedMap;
                    DockerfileRenderer (Target), InstructionParts, DockerQuotes, RunCommands, PackageInstallRenderer
                    ImageBuilder (the fluent API) over InstructionBuilder, MetadataInstructionBuilder,
                    FileInstructionBuilder (AddSources), Built, Checks; Draft, FinalStage, RunMerge;
                    Policy, PolicyCheck, UncleanedInstalls, PolicyViolation
reader/             Dockerfile text -> model, published as dockerfile-reader
                    (package io.github.fiftieshousewife.dockerfile.reader)
                    DockerfileReader (entry), Conversion (the model and its comments); Convert (dispatch),
                    ConvertPairs, ConvertCopy, ConvertAdd, ConvertCommands, ConvertMetadata, Printed, Pairs
                    internal/ (not API; facts, rules and the converter use it): ShellTokeniser (entry): ShellWords,
                    ShellScan, ShellQuotes, ShellSubstitutions, ShellToken (characters to words), ShellCommands,
                    ShellCommand, ShellKeywords (words to commands); Continuations, KeyValuePairs, Expansions,
                    ArgumentText, ArgPairs; Values, Installs
converter/          stage 5: Dockerfile -> model (through reader) -> Dockerfile, and Java builder classes
                    Location; RoundTrip, CanonicalSteps; JavaEmitter, BuilderCalls,
                    RecordCalls, ClassNames, GeneratedClasses; ConverterRun, Coverage, RunFiles; JavaRoundTrip,
                    InMemoryCompiler; Phase 5b: ImageComparisonRun (Builder; BuildahContainer, Checkouts with
                    CloneSource, Processes), DockerfileSelection, Regenerated, LocalSources, Stubs, TarListing,
                    FileEntry, ImageComparison, ConfigDifferences, FileDifferences, ImageComparisons (several at once, timed)
testdata/contracts/ the normal-form contracts between facts and lineage: real Dockerfiles against the history of
                    the real images they built (python-311, sclorg-c9s-*)
testdata/corpus/    the fixture bank: real Dockerfiles and images that broke something, each with its expected
                    outcome; facts', the converter's and lineage's corpus tests ratchet on it (its README)
.github/            workflows: check (every push and pull request: `check` without `verifyUpToDate`), up-to-date
                    (weekly: `verifyUpToDate`); dependabot.yml for the workflows' actions, pinned by commit
LICENSE, NOTICE     Apache 2.0 for the project's own code, put in every published jar (a module with its own, as the
                    parser has, ships those instead)
docs/               overview.md (the problem and the approach), plans/ (one per phase or slice), upstream/
                    (rewrite-docker issues)
```

Each stage's entry point is its `...Main.run(env)`, run through the `cli` module's `docker-inventory <stage>`; the
guides in `docs/guide/` list their settings.

## Build and test

```bash
./gradlew check                                   # compile with -Werror, tests, JaCoCo, SpotBugs, verifyUpToDate
./gradlew check -x verifyUpToDate                 # offline
./gradlew :facts:test --tests '*ShellWordsTest'   # one class
./gradlew roundTrip                               # tag roundtrip: every Dockerfile of a discovery output converted
./gradlew imageRoundTrip                          # tag image-roundtrip: chosen Dockerfiles built twice (Docker)
./gradlew validate                                # tag validate: the validator's counts on a real estate
```

GitHub Actions runs `./gradlew check -x verifyUpToDate` on every push and pull request
(`.github/workflows/check.yml`, its reports kept when it fails) and `verifyUpToDate` weekly (`up-to-date.yml`).

Bulk checks are JUnit tests tagged `roundtrip`, `image-roundtrip` or `validate`, never scripts: `test` and `check`
leave them out, and each skips with its reason when its input (an estate on disk, Docker) is missing. Their
expectations are in `converter/src/test/resources/estate/` and `validator/src/test/resources/estate/`.

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
                facts + lineage ─► report ─► catalogue/dockerfiles.jsonl, archetypes.jsonl, index.html and pages
                facts + lineage ─► rules  ─► findings.jsonl, rates.json, samples; fixes later
                facts           ─► converter ─► roundtrip.jsonl, coverage.json, builder classes
```

**The blob SHA is a Dockerfile's identity.** Content is fetched, stored and parsed once per blob SHA;
`manifest.jsonl` keeps every location that points at it. Never key anything on a path.

**Scope comes from the release database, not the default branch.** A repository without a release-index entry
falls back to its default branch and is flagged, so the report can say how much is not release-scoped.

**The parser is ours.** `parser` is a fork of rewrite-docker 8.90.4, OpenRewrite's last Apache-licensed release
(`docs/plans/parser-fork.md`), still built on OpenRewrite's `rewrite-core`. Only `facts`, `rules` and `converter`
use it; `dsl` never does. It is also published as a library, so its public API (the parser, the tree, the visitors)
changes only with a version that says so.

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
reproduction in `docs/upstream/rewrite-docker-issues.md` and the real lines in the fixture bank. Fix it in `parser`:
a test in its `tree` tests from the minimal reproduction, the grammar (`src/main/antlr`, then
`./gradlew :parser:generateAntlrSources`) or the tree builder (`internal/DockerParserVisitor`) or the printer,
and a line in the changed file's header saying what changed. The corpus tests name the cases the fix turns.

**A new setting.** Read it in the stage's `...Main` from the environment map, give it a default, refuse a bad value
naming the variable, list it in the stage's guide (`docs/guide/`) and in `cli`'s `Commands`. Network settings belong in `http`'s `HttpSettings`.

**Release a library.** Set its version in `gradle.properties` (`inventory.parserVersion`, `inventory.modelVersion`;
semantic: a change to the public API is a major version, or a minor one before 1.0), commit, then run
`./gradlew :parser:releaseToCentral` (or `:dsl:`) with `SIGNING_KEY`, `SIGNING_PASSWORD`, `CENTRAL_TOKEN_USER` and
`CENTRAL_TOKEN_PASSWORD` in the environment. It refuses on a changed tree, a snapshot or an existing tag, uploads the
signed bundle to wait in the Portal until it is published there by hand, and tags `<artifact>-v<version>`.
`./gradlew check` already builds, verifies and zips the same bundle, unsigned, in each library's `build/`.

**Files shared between modules** (`testdata/contracts/`, dsl's goldens) are declared as test inputs in each reading
module's `build.gradle.kts`, so a change reruns the tests that read them.

## Conventions

Enforced by tools:

| Standard | Enforced by |
| --- | --- |
| No compiler warnings | `javac -Xlint:all -Werror` |
| No SpotBugs findings at medium confidence | SpotBugs |
| Published libraries' Javadoc free of errors (broken links, bad HTML) | javadoc `-Xdoclint:all,-missing -Werror` |
| Line coverage at least 80% per module | JaCoCo |
| Compiled by Java 25 (the newest LTS) for Java 21, and tested on 21: the CLI and the libraries run on the LTS users have | Gradle toolchain (`inventory.javaRelease` compiles, `inventory.targetRelease` is the release and the test runtime) |
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
  tests; one assertion per test, several only inside JUnit's `assertAll` (older tests still use `satisfies` or
  `SoftAssertions`: convert one when you touch it); fixtures in `src/test/resources/fixtures/`.
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
