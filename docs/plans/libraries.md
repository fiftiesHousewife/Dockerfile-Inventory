# Publishing a set of libraries: plan

Status: planned 8 Nov; questions open.

## Why

Asked 8 Nov: what stands between this repository and a set of published libraries. The parser is ready to publish
(`parser-fork.md`: `io.github.fiftieshousewife:dockerfile-parser`, its Central bundle built and verified in
`check`), but it is the only one. The Dockerfile model, its renderer and the fluent builder (`dsl`) depend on
nothing and are the obvious second library; the converter (a Dockerfile to the model, and to Java builder code) is
a candidate third. Each was written as a pipeline module: packages named after the inventory, types made public for
the modules beside them, a Java 25 baseline, and a release script for the parser alone.

## What stands in the way, in order

| # | Step | What it takes | Today |
| --- | --- | --- | --- |
| L1 | Choose the set | Which modules are published, their artifact IDs, and whether they share one version | the parser only |
| L2 | Licence | A root `LICENSE` (Apache 2.0) and `NOTICE` for the project's own code, put in every published jar as the parser's are | only `parser/` has them; the POM already claims Apache 2.0 |
| L3 | Library names | Each published module's packages under a library name, as the parser's moved to `io.github.fiftieshousewife.dockerfile.parser` | `dsl` is `...dockerfileinventory.dsl` |
| L4 | The public API, and only it | A `module-info.java` per published module exporting its API packages; what other modules here use but users should not (`Secrets`, `Built`'s raw instructions) moved to an unexported package or a module that is not published | 18 public types in `dsl`, some public only for `rules`, `report` and `converter`; no module descriptors |
| L5 | Settle the API before 1.0 | Decide the shape of `Instruction` (296 lines: its records and builders) and of anything else a user would meet, since changing them after 1.0 is a major version | `Instruction` split open |
| L6 | A Java release users can take | Compile the published modules with `--release` at the chosen LTS, and test them there; the pipeline stays on 25 | everything at 25 |
| L7 | Dependencies chosen on purpose | Each library's dependencies as `api` only where its API exposes them (the parser's tree extends OpenRewrite's), `implementation` otherwise; the converter's (parser, facts, Jackson, OpenRewrite) weighed if it is published | the parser exposes `rewrite-core`, as it must |
| L8 | Javadoc a user can read | Doclint on for published modules; every exported type and public member with a sentence or two on why, as the conventions already ask | doclint off for vendored code; `dsl`'s public types have class Javadoc |
| L9 | Compatibility enforced | A check comparing each library's API with its last release (japicmp), failing a change the version does not allow | the promise in AGENTS.md only |
| L10 | Publication for each | `inventory.published` applied to each library, its POM, and its bundle verified in `check`, as the parser's is | the parser |
| L11 | A release task, not a script | One Gradle task that signs and uploads the chosen libraries' bundles to the Central Portal and tags each `<artifact>-v<version>`, credentials from the environment only; a dry run in `check` | `scripts/release-parser.sh`, for the parser |
| L12 | Docs for users | Per library: coordinates, what it is for, and a short example that is compiled and run as a test, so it cannot rot; the root README points to them | the README is for running the pipeline |
| L13 | The owner's part | Verify the `io.github.fiftieshousewife` namespace on the Central Portal, make a Portal token and a signing key (`parser-fork.md` lists the steps) | not done |
| L14 | First release | Each library at its first version, published from the release task, checked on Central and in a project outside this repository | |

L1 decides L3, L4, L7 and L10; L13 can happen any time before L14.

## Tests, in order

To be written once the questions are answered. The expected shape: a module descriptor per library that the build
compiles and the tests run against (so an unexported type is unreachable); the published modules compiled at the
chosen release; each README example as a test; the japicmp check in `check`; each bundle verified in `check`; the
release task's dry run writing every bundle and its tag names without uploading.

## Questions

1. The set: the parser and `dsl` (the model, renderer, builder and policy), or also the converter, or also facts'
   shell reader?
2. Versions: one version for the whole set, released together, or one per library?
3. The Java release the libraries target: 21 (records, sealed types and pattern switches all work there), 17, or 25?
4. The first version: `0.1.0`, saying the API may still change, or `1.0.0`, promising it will not without a major
   version?
5. `dsl`'s artifact ID and package: for example `dockerfile-model` and `io.github.fiftieshousewife.dockerfile.model`,
   or a name that leads with the builder?
6. `Instruction`: split it (each instruction's record and its builder methods apart) before the first release, or
   keep it as it is?
