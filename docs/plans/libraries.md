# Publishing a set of libraries: plan

Status: planned 8 Nov; questions answered 8 Nov. Done: step 1, the licence; step 2, `Secrets` in its own module and `dsl` renamed to
`io.github.fiftieshousewife.dockerfile.model` (the estate round trip unchanged); step 3, `Instruction` split into a record per file; step 4, module names; step 5, Java 21 (both compiled with
`--release 21` and tested on a 21 runtime, nothing to rewrite); step 6, Javadoc (doclint on as an error for published modules, `-missing`
aside: a sentence on every type and every method a user calls, none on compact constructors or enum constants);
step 7, `dockerfile-model`'s bundle verified in `check`.

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

## What the answers settle

- **The set**: `dockerfile-parser` (the `parser` module, 1.0.0) and `dockerfile-model` (the `dsl` module, packages
  `io.github.fiftieshousewife.dockerfile.model`, first 0.1.0); the converter stays a pipeline module.
- **Versions**: one each, `inventory.parserVersion` and `inventory.modelVersion`, released apart, tags
  `dockerfile-parser-v<version>` and `dockerfile-model-v<version>`.
- **Java**: both libraries compiled with `--release 21` and their tests run on a 21 toolchain; every other module
  stays on 25.
- **`Instruction`**: each instruction's record top-level in its own file (`Copy`, `Add`, `Run`, ...), still the
  sealed interface's permitted types, before 0.1.0.
- **What leaves `dsl`'s API**: `Secrets` (shared by facts, rules, report and the converter) moves to a small module
  that is not published; `Built`'s raw instructions and anything else only the converter needs move to an unexported
  package of the model.

## Functional gaps (asked 8 Nov: does publishing need features too?)

Measured on the combined estate (5,250 Dockerfiles, 93 unparsed): 51 are templates, about 25 use balena's
`CROSS_BUILD_COPY` preprocessor, a handful are not Dockerfiles (SVG, XML, an editor syntax file) and one is broken for
Docker too. What a library user would still miss:

- **F1, the parser's last bugs**, before 1.0: an exec-form `CMD [...]` on the last line with no newline (`04ef8555`);
  shell quoting inside `$(...)` in a `RUN`, as jq filters write it (`4059fc1f`, `7bd844c2`); a `LABEL` value ending
  in a quote before a continuation (`8a8eb8c4`). Each from its minimal reproduction, as AGENTS.md says.
- **F2, a plain entry point**: `parse(String)` returning the tree, and a syntax error carrying its line and column,
  beside OpenRewrite's `Parser` API, which returns a stream of source files to cast.
- **F3, reading** (answered 8 Nov: a third library): `dockerfile-reader`, Dockerfile text to the model, built on
  `dockerfile-parser` and `dockerfile-model` from the converter's reading half (`Convert` and its helpers); the
  model stays dependency-free and the Java builder generation stays internal. First version 0.1.0.
- **F4, the default render target**: BuildKit's features (heredocs, `RUN --mount`) allowed by default, the older
  syntax a choice; the one bind-mount round trip that fails today fails on the narrow default.

## Found on the way

- Step 4 (8 Nov): `rewrite-core` declares no module name, so Gradle keeps it on the classpath and a named module
  cannot require it, short of a name derived from its file, which Maven warns against. The parser therefore names
  its module in its manifest (`Automatic-Module-Name: io.github.fiftieshousewife.dockerfile.parser`) and its
  `.internal` packages are internal by name only; `dockerfile-model` is a full named module exporting its one
  package. The bundle check requires one or the other of every published jar.

## Tests, in order

One assertion each, several in `assertAll`.

1. Licence: the root `LICENSE` and `NOTICE` in each published jar (the bundle check grows to look).
2. `Secrets` in its own unpublished module; its users' tests unchanged. Then the package rename of `dsl` to
   `dockerfile.model`, a refactor with every test green and the converter's goldens byte for byte the same.
3. The `Instruction` split, a refactor: renderer goldens and the converter's round trip byte for byte the same.
4. Module descriptors: `module-info.java` for both libraries, exporting only their API; a test from outside the
   module that an unexported type cannot be reached.
5. `--release 21`: both libraries compile and their tests pass on 21; what uses a newer feature is rewritten, each
   with its test.
6. Doclint on for both; the missing Javadoc written.
7. `inventory.published` on `dockerfile-model`, its POM and bundle verified in `check`.
8. japicmp in `check` for each library against its last release (none yet: the check runs once a release exists).
9. A Gradle release task replacing `scripts/release-parser.sh`: its dry run in `check` writes both signed (or, without
   a key, unsigned) bundles and names the tags; the upload only with the Portal token in the environment.
10. Each library's README example compiled and run as a test.
11. F1: each parser bug from its minimal reproduction, in the parser's `tree` tests; the corpus tests name what turns.
12. F2: `parse(String)` on a real Dockerfile; a syntax error's line and column.
13. F3: `dockerfile-reader` as a module (the converter's reading half moved, the converter then built on it, its
    round trip byte for byte the same), with its descriptor, Java 21, Javadoc, bundle and README example as above.
14. F4: the default target renders heredocs and `RUN --mount`; the older target still refuses them.
15. After the owner's Central setup: all three released, then used from a project outside this repository on
    Java 21.

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

Answers (8 Nov): 1, the parser and `dsl`; 2, one version each; 3, Java 21; 4, `dockerfile-model` at 0.1.0, the parser
at 1.0.0; 5, `dockerfile-model`, package `io.github.fiftieshousewife.dockerfile.model`; 6, split `Instruction` first.
Asked after (8 Nov): reading published as a third library, `dockerfile-reader` (F3).
