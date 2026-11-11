# Publishing the CLI: plan

Status: planned 11 Nov; questions answered 11 Nov; done 11 Nov: `inventory.published` split onto
`inventory.central`, the three libraries' bundles unchanged byte for byte (the `.module` files, which carry each
jar's checksums, included); `inventory.distributed` on `cli`, its bundle built and verified in `check`, the command in
its zip run with `--version`. The release waits on the owner.

## Why

The libraries' bundles are built and verified in `check` (`libraries.md`). A company runs the pipeline from what its
Artifactory mirrors of Maven Central, so the `docker-inventory` command has to be on Central too. Its jar cannot be
published as a library: it depends on ten pipeline modules (survey, discovery, facts, lineage, report, rules, validator,
converter, http, secrets) that are not published, so its POM would name artifacts nobody can resolve, and publishing
them would make their APIs public for good.

## The design

- **Coordinates**: `io.github.fiftieshousewife:docker-inventory:<inventory.cliVersion>`, first 0.1.0; tag
  `docker-inventory-v<version>`.
- **The artifact**: the distribution zip (`distZip`: `bin/docker-inventory`, `bin/docker-inventory.bat`, `lib/` with
  every jar the command runs on), published as `docker-inventory-<version>.zip`, POM `<packaging>zip</packaging>`
  and no dependencies, since everything it needs is inside. No Gradle module metadata: there is no Java component to
  describe, and a Gradle user fetches the zip by its extension
  (`io.github.fiftieshousewife:docker-inventory:0.1.0@zip`).
- **Licence**: the root `LICENSE` and `NOTICE` at the top of the zip, beside `bin/` and `lib/`; the parser's jar
  inside keeps its own.
- **Central's other files**: a `-sources.jar` and a `-javadoc.jar`, which Central requires of any packaging but `pom`
  (see what the answers settle).
- **One plugin for what both share**: `inventory.published` split in two. `inventory.central` holds the POM's
  common parts (url, licence, developer, scm), the `build/central` repository, signing from the environment, the
  bundle's zip and `releaseToCentral`; `inventory.published` keeps the library parts (sources and Javadoc jars from
  the Java component, doclint, LICENSE in the jar, the jar's module name) and `inventory.distributed` (new) the
  distribution's. `verifyBundle`'s required-file check is shared and takes the main file's extension.
- **Gated in `check`**: `cli/build/docker-inventory-<version>-bundle.zip` built and verified on every build, as the
  libraries' are.

## Tests, in the order they will be written

build-logic's tests cover the logic in plain Kotlin, as `CentralReleaseTest` does; the plugins themselves are
checked by `check` building and verifying the real bundles.

1. `CentralBundle.missing(dir, stem, "zip")` names a missing `.zip`, `-sources.jar`, `-javadoc.jar`, `.pom` or their
   checksums (the libraries' check moved out of the script, then given the extension).
2. `CentralBundle.pomGaps(pom)` names each element Central requires that a POM lacks.
3. `CentralBundle.distributionGaps(zip, stem)` names a zip without `<stem>/bin/docker-inventory`, a `lib/` jar, or
   `LICENSE` and `NOTICE`.
4. The bundles: the three libraries' byte for byte as before (bar timestamps), the CLI's built and verified.

## What the answers settle

- **Sources**: the `-sources.jar` holds the main sources of `cli` and of every module it ships, so each jar in
  `lib/` can be read from Central.
- **Javadoc**: the `-javadoc.jar` holds one `README.md` saying the artifact is an application with no API and
  where its documentation is, as Central allows.
