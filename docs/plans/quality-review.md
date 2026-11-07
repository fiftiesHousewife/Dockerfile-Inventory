# Code and documentation review: plan

Status: built 7 Nov.

The owner asked for a detailed overhaul of the documentation and the code: comments stripped to what is needed
(Javadoc only on API classes), classes small and neat, methods package-private and tested on their own. The code grew
over seven estates and three reviews (about 11,000 lines in seven modules), and it shows:

| Module | Main lines | Javadoc blocks | Private methods | Classes over 150 lines |
| --- | --- | --- | --- | --- |
| discovery | 961 | 40 | 25 | GitHubHttp 222 |
| facts | 1,999 | 124 | 77 | FactExtractor 313, ShellWords 298, ShellCommands 216 |
| lineage | 1,778 | 98 | 57 | Links 176, ArtifactoryHttp 162 |
| report | 1,904 | 71 | 76 | Dashboard 363, Charts 190 |
| rules | 1,231 | 43 | 22 | RulesRun 237 |
| dsl | 941 | 55 | 22 | ImageBuilder 374 |
| converter | 900 | 36 | 34 | Convert 266, ConverterRun 173 |

## Rules (decided here, recorded in AGENTS.md's conventions)

1. **API classes.** A type is API when another module imports it, it is a run entry point (`...Main`), or an API
   type's public signature exposes it. Measured by imports on 7 Nov:
   - facts: `FactsMain`, `FactsParser`, `FactExtractor`, `ParseResult`, `ImageFacts`, `BaseImage`, `EntrypointForm`,
     `NormalForms`, `Steps`, `Step`, `ShellTokeniser`, `Continuations`, `KeyValuePairs`, and what their signatures
     expose (`UserKind`, `PackageCall`, `StagedCall`, `Rates` if exposed);
   - dsl: `Dockerfile`, `Stage`, `Instruction`, `RunBody`, `BaseImage`, `PackageInstall`, `PackageManager`,
     `DockerfileRenderer`, `Target`, `ImageBuilder`, `Policy`, `PolicyViolation`, `Secrets`, and what their signatures
     expose (`Built`);
   - discovery, lineage, report, rules, converter: their `...Main` only.
   Every other type becomes package-private (records serialised by Jackson included; if Jackson cannot reach one,
   that is found by the tests, and it stays public with the reason in the commit message).
2. **Javadoc only on API classes**: the class, and its public members, a sentence or two saying why. Everything else
   loses its Javadoc. A `//` comment stays only where the code cannot say why and the reason would otherwise be lost:
   a workaround for an upstream bug (`// rewrite-docker issue 9 (docs/upstream/)`), or an external format's quirk
   that the test names cannot carry. "The owner's choice, <date>" and history ("was", "now") go: the plans record
   decisions. No commented-out code.
3. **Small classes**: production classes at most 150 lines; a class holds one job. Each class above is split along
   its jobs, keeping the public surface other modules use unchanged (a facade where needed).
4. **Methods**: a method with any decision or computation (a branch, a loop or stream with logic, a regex) is
   package-private and has a direct test. `private` stays only for one-line helpers without a branch. Public methods
   only on API classes. No method longer than about 25 lines.
5. **Tests**: one test class per production class it tests directly; names say what is checked; no comments inside
   tests; several checks on one result in `satisfies` or `SoftAssertions`; fixtures in `src/test/resources/fixtures/`.
6. **Behaviour does not change.** Every existing test stays green (a test may move to the class its method moved
   to); the outputs of facts, rules, the converter and lineage on openshift, docker-library and okd are compared
   before and after, byte for byte.
7. **Gates unchanged**: `-Werror`, SpotBugs, JaCoCo 80% per module, `verifyUpToDate`.

## Splits planned

| Class | Into |
| --- | --- |
| facts `FactExtractor` (313) | `FactExtractor` (the public entry points), `StageScan` (one stage's walk), `RunTally`, `BaseImages` (final base, split reference), `BuildKitFeatures` |
| facts `ShellWords` (298) | `ShellWords` (the scanner), `ShellQuotes` (single, double, escapes), `ShellSubstitutions` (`$( )`, backticks, arrays) |
| facts `ShellCommands` (216) | `ShellCommands` (operators, command end), `ShellKeywords` (`if`, `case`, loops, functions, `for` lists) |
| lineage `Links` (176) | `Links` (the run), `Alignment` (scoring), `Candidates` (labelled repository, base path) |
| lineage `ArtifactoryHttp` (162) | `ArtifactoryHttp` (requests), a response reader |
| discovery `GitHubHttp` (222) | `GitHubHttp` (requests), `GraphQlBlobs`, `RestTrees` |
| report `Dashboard` (363) | `Dashboard` (page assembly) and one class per section (`HeadlineSection`, `RatesSection`, `BasesSection`, ...) |
| report `Charts` (190) | one class per chart kind |
| rules `RulesRun` (237) | `RulesRun` (the run), `RuleRates`, `Samples`, `FindingsTable` |
| dsl `ImageBuilder` (374) | `ImageBuilder` (the fluent API), `PolicyCheck`, builders per instruction group |
| converter `Convert` (266) | `Convert` (dispatch), `ConvertPairs` (ENV, LABEL, ARG), `ConvertCopy` (COPY, ADD, heredocs), `ConvertRun` |
| converter `ConverterRun` (173) | `ConverterRun`, `Coverage`, `OptIn` |

Each module's own reading may find a better line; the limit and the unchanged public surface are what matter.

## Order

1. One module at a time, each on its own branch: discovery with build-logic, facts, lineage, report, rules, dsl,
   converter. Per module: visibility (rule 1), Javadoc and comments (2), splits (3), methods and their tests (4, 5),
   `./gradlew :<module>:check` green, commit.
2. Merge the branches, `./gradlew check` green.
3. Outputs compared before and after (rule 6).
4. Documentation, read through in full:
   - README: example reports linked (the published OKD and bitnami dashboards), every setting and output current;
   - AGENTS.md: layout rows for every new class, the conventions (rules 1-5) replacing "Javadoc explains why";
   - BACKLOG.md, every plan's status line (phase-0's "slices 3 and 4 wait on the GHE token" is out of date), the
     fixture READMEs, `docs/upstream/rewrite-docker-issues.md`, `testdata/contracts/README.md`;
   - the delivery plan document.
5. Push.

## Not in scope

Behaviour, rule semantics, output formats; lineage's history normaliser and facts' parser stay independent readers
(the contracts compare them).

## As built

Seven modules reviewed on their own branches and merged, each with `./gradlew check` green:

| Module | Largest class before -> after | Tests before -> after | Public types after |
| --- | --- | --- | --- |
| discovery | GitHubHttp 222 -> GitHubRequests 112 | 108 -> 179 | DiscoveryMain |
| facts | FactExtractor 313 -> ShellWords 137 | 166 -> 347 | the API list and the types it exposes |
| lineage | Links 176 -> ArtifactoryHttp 110 | 103 -> 193 | LineageMain |
| report | Dashboard 363 -> LineageFiles 106 | 75 -> 190 | ReportMain |
| rules | RulesRun 237 -> SecretValue 77 | 42 -> 158 | RulesMain |
| dsl | ImageBuilder 374 -> 127 | 37 -> 92 | the API list, `Policy.Approved`, `PolicyViolation.Breach` |
| converter | Convert 266 -> 119 | 34 -> 108 | ConverterMain |

Jackson reads and writes every package-private record, so none had to stay public. Javadoc is left on API types
only; the `//` comments kept name an upstream bug or an external format's quirk.

Outputs compared before and after on openshift and docker-library (facts, rules, converter) and okd (lineage): all
identical except `effective.jsonl`, whose `env` and `labels` came out in an order `Map.copyOf` salts per JVM run, so
the file had never been reproducible; it is now written in key order (and tested). The converter's agent also
compared all 5,250 combined files' round trips and generated classes: identical.

AGENTS.md was rewritten for contributors (the layout with every class, the conventions above); the README stays for
users and links the new overview, `docs/overview.md`.

