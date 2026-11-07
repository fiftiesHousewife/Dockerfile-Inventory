# Forking the Dockerfile parser: plan

Status: planned 7 Nov; all questions answered 7 Nov. F1 built (the fork, unchanged but for its packages); the
publication setup built; the 15 fixes next.

## Why

The owner expects no upstream fixes: OpenRewrite is moving to source-available licences. What was found (7 Nov):

- We use `org.openrewrite:rewrite-docker` 8.90.4. Its POM declares the Apache License 2.0; its source is public in
  `openrewrite/rewrite` under `rewrite-docker/` (build commit `398a6349648ca802aa050bdb6fe45a99395dce47`).
- 8.90.4 (24 Aug 2026) is the newest on Maven Central; nothing has been published since, unusual for OpenRewrite.
- The separate `openrewrite/rewrite-docker` repository (recipes) was archived on 26 Jan 2026 and carries the Moderne
  Source Available License; OpenRewrite's licensing page puts its newer language modules under that licence and does
  not name Docker either way.
- We have 15 parser bugs with minimal reproductions (`docs/upstream/rewrite-docker-issues.md`): 12 worked around by
  rewriting text before a second parse (`RetryText`), 3 with no safe workaround, counted as parse failures.

Apache 2.0 allows a fork: copy, change, ship inside the company, keeping the licence and notices and marking what we
changed.

## Design

| # | Step |
| --- | --- |
| F1 | The parser module at 8.90.4 copied from `openrewrite/rewrite@398a634` (`rewrite-docker/`: the ANTLR grammar, the tree, the printer, the visitors), with its `LICENSE` and `NOTICE`, under question 1's home and a new group so it cannot be mistaken for upstream |
| F2 | It still depends on the published `rewrite-core` 8.90.4 (Apache 2.0, unchanged), so facts, rules and the converter change only their dependency |
| F3 | Each of the 15 bugs fixed in the grammar or printer, test-first from its minimal reproduction; as each is fixed, its `RetryText` workaround and its note go (question 2) |
| F4 | `verifyUpToDate` stops checking `rewrite-docker` (it is ours now) but keeps checking `rewrite-core` |
| F5 | Measured on all six estates before and after: parse failures, unreadable RUNs, round trip; the fixture bank (`fixture-bank.md`) pins every case |

## Questions

1. Where the fork lives: a module in this repository (`parser/`, simplest, built and tested with everything else), or
   its own repository published to the internal Artifactory (reusable by other teams, a release step of its own)?
2. Bugs: fix all 15 in the fork and delete `RetryText` as each goes, or fork first with no change and fix them one
   by one later?

Answers (7 Nov): 1, a `parser` module in this repository; 2, fork unchanged first (outputs identical), then fix all 15
test-first, deleting each `RetryText` workaround and its note as its bug is fixed.

## Publication (asked 7 Nov)

The owner wants the parser deployable as an independent library: a jar other teams and tools can depend on, built
from the `parser` module here and published with its own coordinates and versions.

What that needs:

| # | Item | Proposal |
| --- | --- | --- |
| P1 | Packages | Our own (question 3): with upstream's `org.openrewrite.docker` kept, a project that also has upstream's jar gets two copies of the same classes (split packages, whichever loads first wins); renaming keeps the two apart and makes the fork unmistakable |
| P2 | Coordinates | A group we own (question 4), artifact `dockerfile-parser`, the package to match |
| P3 | Versions | Its own semantic version, starting `1.0.0` for the fork with its 15 fixes; the upstream base recorded in the jar's manifest (`Based-On: org.openrewrite:rewrite-docker:8.90.4@398a634`) |
| P4 | What is published | The jar, a sources jar and a Javadoc jar (most repositories, and Maven Central, require them), a POM naming the Apache License 2.0, the developers, the SCM, and its dependencies (`rewrite-core` 8.90.4, ANTLR runtime) with versions pinned |
| P5 | Licence and notices | Apache 2.0 kept: `LICENSE` and a `NOTICE` in the jar, each upstream file keeps its header, each file we change says so (Apache 2.0, section 4) |
| P6 | Where | Question 5: the company's Artifactory (internal), GitHub Packages, or Maven Central (public; needs a verified namespace and signed artifacts) |
| P7 | How | Gradle's `maven-publish` (and `signing` for Central) in a `inventory.published` convention plugin; a release from a tag, credentials from the environment only (never a file in the repository), a dry run to a local folder in `check` so the POM and jars are tested |
| P8 | API surface | What the library promises: `DockerParser`, the `Docker` tree, `DockerVisitor`/`DockerIsoVisitor`, the printer; `internal` stays internal (and the Java module, if any, exports only the public packages) |

Questions:

3. Packages: rename to our own (e.g. `io.github.fiftieshousewife.dockerfile.parser`, with `tree`, `internal`), or keep
   `org.openrewrite.docker` (less change in facts, rules and the converter, but it clashes with upstream's jar)?
4. Group ID: `io.github.fiftieshousewife` (a namespace GitHub's account gives for Maven Central), or the company's own
   (for example `com.<company>.platform`), or another?
5. Repository: the company's Artifactory, GitHub Packages, or Maven Central?

Answers (7 Nov): 3, our own packages, `io.github.fiftieshousewife.dockerfile.parser` (`.tree`, `.internal`,
`.internal.grammar`); 4, group `io.github.fiftieshousewife`, artifact `dockerfile-parser`; 5, Maven Central.

Maven Central asks for things only the owner can do, done once before the first release:

1. Sign in to the Sonatype Central Portal (central.sonatype.com) with the GitHub account `fiftiesHousewife`, which
   verifies the `io.github.fiftieshousewife` namespace.
2. Make a Portal user token, kept in the release environment as `CENTRAL_TOKEN_USER` and `CENTRAL_TOKEN_PASSWORD`.
3. Make a GPG key for signing, publish its public half to a key server, and keep the private key and passphrase in
   the release environment (`SIGNING_KEY`, `SIGNING_PASSWORD`), never in the repository.

The build does the rest: `maven-publish` and `signing` write a signed bundle (jar, sources, Javadoc, POM, checksums)
to `parser/build/central`, checked in `./gradlew check` without signing; a release task uploads the bundle to the
Portal's publisher API with the token. Releases are cut from a tag `parser-v<version>`.

## F1 as built (7 Nov)

- `parser/`: the parser core of `openrewrite/rewrite@398a634` `rewrite-docker/` (`DockerParser`, `DockerVisitor`,
  `DockerIsoVisitor`, `DockerParsingException`, `internal` with the tree builder and printer, `tree`, the grammar),
  packages renamed to `io.github.fiftieshousewife.dockerfile.parser`; recipes, search, traits, tables and their data
  left out. Upstream's parser and tree tests come with it (with `Assertions`, now a test helper).
- The lexer and parser regenerate from `src/main/antlr` with ANTLR 4.13.2 (`./gradlew :parser:generateAntlrSources`)
  and match upstream's generated code apart from whitespace.
- `inventory.vendored` (build-logic): inventory.java's toolchain, tests and coverage gate, with Lombok; no `-Werror`
  or SpotBugs for code in upstream's style; the generated `grammar` package out of coverage (the rest: 88%).
- Facts, rules and the converter depend on `:parser` instead of `org.openrewrite:rewrite-docker`; their imports
  renamed.
- `LICENSE`, `NOTICE` and a README in `parser/`; each copied file's header says the package was renamed.
- Checked: `./gradlew check` green (with `verifyUpToDate`); the corpus tests unchanged; facts, rules and the
  converter on openshift and docker-library and lineage on okd give byte-identical outputs before and after (40
  files).

## Publication as built (7 Nov)

- `inventory.published` (build-logic): `maven-publish` with sources and Javadoc jars, a POM with name, description,
  URL, the Apache License 2.0, the developer and the SCM, resolved dependency versions (`rewrite-core` 8.90.4, ANTLR
  runtime 4.13.2); signing only when `SIGNING_KEY` is in the environment. `check` writes the bundle to
  `parser/build/central` and `verifyBundle` checks every file and POM element Central requires.
- The jar carries `META-INF/LICENSE`, `META-INF/NOTICE` and `Based-On: org.openrewrite:rewrite-docker:8.90.4@398a634`
  in its manifest; version from `inventory.parserVersion` (1.0.0).
- `scripts/release-parser.sh`: refuses without the signing key and the Portal token, or on a changed working tree or
  an already-tagged version; builds and signs, zips the bundle (no `maven-metadata.xml`), uploads it to the Central
  Portal as user-managed (it waits there to be published by hand), tags `parser-v<version>`.
- Not yet done, and the owner's: the namespace, the Portal token and the GPG key (above). The first release waits on
  the 15 fixes.

