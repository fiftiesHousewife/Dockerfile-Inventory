# Completing the DSL: plan

Status: planned 7 Nov; questions answered 7 Nov. S1 done 7 Nov (every instruction on the combined estate typed);
S2 done 8 Nov (ten goldens, one per family); S3 done 8 Nov (every passing Dockerfile's class rebuilds it); S4
built 8 Nov as the tagged test `./gradlew imageRoundTrip` (see "Round-trip tests on any estate"), its full run the
owner's.

The owner asked for the DSL to be made completely ready. It works: an immutable model, one renderer, policy-enforcing
builders, four goldens passing every rule, and a converter that round-trips 5,149 of the combined estate's 5,250 files
with 92.6% of each file's instructions typed on average (3,206 files fully typed). What is missing is in four slices,
each test-driven and measured on the combined estate and the fixture bank.

## S1. Every instruction typed

The converter keeps as `Raw` (rendered as written, listed for review) what the model cannot type. On the combined
estate, by reason:

| Raw today | Uses | Model added | Renders |
| --- | --- | --- | --- |
| `ADD` | 1,080 | `Add(sources, destination, chown, chmod, checksum, keepGitDir)` | `ADD [--chown=..] [--chmod=..] [--checksum=..] [--keep-git-dir] src... dest` |
| `RUN` with flags | 433 | `Run(body, flags)` with `RunFlag`: `Mount(options)`, `Network(mode)`, `Security(mode)`, in written order | `RUN --mount=type=cache,target=/c ...`; refused unless the target allows mounts |
| `SHELL` | 346 | `Shell(command)` | `SHELL ["/bin/bash", "-c"]` |
| shell-form `CMD` | 337 | `Cmd(command, form)`, form exec or shell (question 1) | `CMD node server.js` |
| `VOLUME` | 304 | `Volume(paths)` | `VOLUME ["/data"]` |
| `RUN` exec form, heredoc | 196 | `RunBody.Exec(arguments)`, `RunBody.Heredoc(command, delimiter, quoted, lines)` | `RUN ["a", "b"]`; `RUN <<EOF ... EOF`, refused unless the target allows heredocs |
| `EXPOSE` with protocol, range, variable, several | 160 | `Expose(ports)` with `Port(spec)`: `8080`, `53/udp`, `8000-8010`, `$PORT` | `EXPOSE 8080 53/udp` |
| `MAINTAINER` | 153 | `Maintainer(name)` (deprecated by Docker: the converter keeps it, the builders do not offer it) | `MAINTAINER name` |
| `STOPSIGNAL` | 117 | `StopSignal(signal)` | `STOPSIGNAL SIGTERM` |
| `COPY --chmod`, `--link`, JSON form, heredoc | 118 | `Copy` gains `chmod`, `link`, `jsonForm`; `CopyHeredoc(destination, delimiter, quoted, lines)` | as written; heredoc refused unless the target allows it |
| shell-form `ENTRYPOINT` | 59 | `Entrypoint(command, form)`; the builders offer exec only, H2 still finds shell form | `ENTRYPOINT exec app` |
| `ONBUILD` | 28 | `Onbuild(instruction)` | `ONBUILD` + the inner instruction |
| `HEALTHCHECK` | 21 | `Healthcheck(command, interval, timeout, startPeriod, retries)` or `Healthcheck.NONE` | `HEALTHCHECK --interval=30s CMD ...`, `HEALTHCHECK NONE` |

For each: the model record (validated), the renderer, the converter's mapping (the `Raw` reason goes), `JavaEmitter`'s
code and the `ImageBuilder` method it calls (so generated classes compile), policy where one applies (question 2).
`Expose(int)` becomes `Expose(List<Port>)`: a change of the dsl's API, used only inside this repository.

Measure: the typed share from 92.6% towards 100% (whatever stays `Raw` is named), the round trip unchanged or better
(5,149), the corpus round-trip cases unchanged.

## S2. Goldens from real archetypes

Today's four goldens were hand-picked before any estate run. Replace them with the largest real archetypes (question
3): for each, the representative Dockerfile is converted, then rewritten as the policy says it should be (approved and
pinned base, non-root user, group-0 writable paths, cleaned installs, exec-form start), built with `ImageBuilder`, and
its rendered Dockerfile kept as the golden. The acceptance test stays: every golden passes every rule with no finding.
Each golden's README line names the archetype it stands for and what the rewrite changed.

## S3. The Java round trip

Today one test compiles a generated builder class. For every Dockerfile that passes the text round trip: emit its
class, compile all of them in one in-memory compilation (`javax.tools`), load each, build, render, and compare with the
original as the text round trip does. `roundtrip.jsonl` gains `javaPassed`; `coverage.json` the Java pass rate.
Measure: the Java pass count equals the text pass count; any difference is a bug in `JavaEmitter` or the builders.

## S4. Building and comparing images (Phase 5b)

The round trips prove the text; this proves the image. For a chosen set of Dockerfiles (question 6), build the
original and the regenerated Dockerfile from the same context at the same commit, with the same base, and compare the
two images (question 5). A Java comparison (`ImageComparison`: two image configs and two file listings -> the
differences) is unit-tested here; a script builds and feeds it, run where Buildah can run (question 4), as
`lineage-demo.sh` runs.

## Questions

1. Shell-form `CMD` and `ENTRYPOINT` (396 uses): typed with a `form` (exec or shell), the builders still offering exec
   only and rule H2 still finding shell-form entrypoints; or kept `Raw` (reviewed one by one)?
2. `ADD` in the builders (new Dockerfiles): offered only for a URL or an archive (a local file must be `copy`, as
   Docker's guidance and the rules prefer), or freely?
3. Goldens: how many (proposed: the ten largest archetypes, bundles and templates aside), and rewritten to the policy
   as above, or kept exactly as the archetype builds today?
4. Where Buildah runs for S4: a local script in a Buildah container (as the lineage demo), a rootless Buildah job in
   an OpenShift pod, or a Tekton task?
5. What counts as the same image: the config (user, environment, entrypoint, command, working directory, exposed
   ports, labels, volumes) and every file's path, mode, owner and content hash, ignoring timestamps; or less?
6. Which Dockerfiles S4 compares: each golden's archetype representative, a sample across organisations, or every
   opted-in file?

Answers (7 Nov): 1, typed with a form; 2, `add` only for a URL or an archive (a plain local file is refused with
"use copy"); 3, the ten largest archetypes, rewritten to the policy; 4, a local script first; 5, the config and every
file's path, mode, owner and content hash, timestamps and build-time labels ignored; 6, the ten golden archetypes'
representatives first.

## S2 in detail

**Choosing the ten.** One golden per family of image, the largest archetype of each family standing for it, from
the combined estate's archetypes (`out/all/report/archetypes.json`); metadata-only bundles, parse failures,
templates, bases that are only an unresolved build argument and Windows images set aside:

| Family | Rank | Locations | Representative's final base |
| --- | --- | --- | --- |
| bitnami application image (download, unpack, non-root) | 2 | 129 | `bitnami/minideb:bookworm` |
| OLM file-based catalog (`opm serve`) | 3 | 97 | `openshift4/ose-operator-registry-rhel9` |
| Go operator built in a builder stage (the other Go and distroless archetypes fold in) | 4 | 84 | `ocp/5.0:base-rhel9` |
| language runtime built from source (docker-library PHP; Alpine PHP, Tomcat, Python, Postgres fold in) | 8 | 54 | `debian:bookworm-slim` |
| minimal root filesystem assembled with `rpm --root`, copied onto `scratch` | 10 | 46 | `scratch` |
| prebuilt binary added onto a base, no build | 11 | 45 | `ubi9/ubi-minimal` |
| tool image, packages installed by a script | 26 | 23 | `ubi9/ubi-minimal` |
| Java service on UBI with OpenJDK | 27 | 22 | `ubi8/ubi-minimal` |
| s2i builder image (sclorg) | 42 | 15 | `sclorg/s2i-base-c9s` |
| Python application on a UBI language image | 67 | 10 | `ubi9/python-311` |

**The base.** Each golden keeps its family's base, approved in the golden policy and pinned by digest; the rewrite
changes the user, permissions, cleaning and start form, and keeps the archetype's package manager. One approved
list, `dsl/src/test/resources/golden/allow.csv`, is read by `Goldens` and by `rules`' acceptance test.

**Each golden.** The representative converted with `ConverterMain`'s builder class as the starting point, then
rewritten by hand to the policy (approved base pinned by digest, numeric non-root user, group-0 writable paths,
installs cleaned in their `RUN`, exec-form start) in `Goldens`; `build(POLICY)` must succeed, its rendering is the
committed `golden/<name>.Dockerfile`, and the golden folder's README gives one line per golden: the archetype's
rank and signature, the representative's blob and repository, and what the rewrite changed. The four hand-picked
goldens go. The acceptance test is unchanged: `rules`' `GoldenAcceptanceTest` runs every rule on every golden and
expects no finding.

**Tests, in order.** First, with the tests green, the approved list moved to `golden/allow.csv` and read by both
modules. Then for each golden in the order above: its name in `GoldenTest`, failing for want of the builder
and the file; the builder; the rendered file committed after reading it against the representative. Then a test that
every golden has its README line, and the acceptance test run over all ten. The old four are removed last.

**Questions.** 1. Which ten: answered 8 Nov, one per family, as above. 2. The base: answered 8 Nov, each family's
own base approved in the golden policy, pinned.

### S2 as built

Ten goldens in `dsl/src/test/resources/golden/`, one per family, each with its README line (what the archetype is,
where its representative lives, what the rewrite changed); `GoldenTest` checks each file against its builder and
that the README describes exactly the goldens there are; `rules`' acceptance test finds nothing in any of them; one
`allow.csv` serves both. The four hand-picked goldens are gone.

Rewriting real Dockerfiles to the policy found three faults, each fixed test first:

- The group-0 step went before every `USER` in the final stage, so a stage that switched to `USER 0` to build put
  it before the paths existed (python-on-ubi); it now goes before the last `USER` only.
- The policy asked `scratch` for a digest, though there is none and rule R1 already exempts it (rpm-rootfs).
- H1 in the builders missed `yum -y clean all`, a flag before `clean`, as sclorg writes it (s2i-php); rule H1,
  which reads facts, already accepted it.

It also raised a question, answered 8 Nov: the builders gave every unowned final-stage `COPY` to the runtime user
(`--chown=<user>:0`), so scripts copied to `/usr/local/bin` were writable by the process. Unowned copies now go to
root (`--chown=0:0`); what the process writes to is declared with `writablePaths`.

## Between S2 and S3: faithful calls without long argument lists

Asked 8 Nov: the faithful builder methods take long positional lists (`copy` with seven arguments, `add` with
eight, `runHeredoc` with five), and every generated class calls them. Instead, the records with many optional parts
get a factory and immutable "with" methods, and the builders take the record:

```java
.copy(Instruction.Copy.of("run.sh").to("/usr/local/bin/").from("build").mode("755").linked())
.add(Instruction.Add.of("https://example.com/a.tgz").to("/opt/").checksum("sha256:..."))
.copyHeredoc(Instruction.CopyHeredoc.to("/etc/app.conf").lines("a = 1"))
.run(List.of(mount), RunBody.Heredoc.of("set -e", "make").command("bash"))
```

`Copy.of(sources)` and `Add.of(sources)` return the sources alone, which `to(destination)` makes an instruction, so
a destination cannot be forgotten. With-method names differ from the accessors (`from`, `ownedBy`, `mode`,
`linked`, `inJsonForm`, `keepingGitDir`; for heredocs `delimitedBy`, `expanding`, `command`). Heredocs default to
`EOF`, quoted (no expansion). The builders keep their short methods (`copy(src, dest)`, `copyFrom`, `run`,
`runExec`, `install`); `run(flags, body)` replaces the faithful `install(runFlags, ...)`, `runExec(flags, ...)` and
`runHeredoc(...)`. `BuilderCalls` writes only what a Dockerfile sets, so most generated calls stay one short line.

As built 8 Nov: as above; the four-argument `copy(sources, destination, chown, fromStage)` went too, and
`RecordCalls` writes the record expressions for `BuilderCalls`.

Tests, in order, one assertion each (several inside `assertAll`): `Copy.of(...).to(...)` and each with-method;
the builder's `copy(Copy)`; the same for `Add`, `CopyHeredoc`, `RunBody.Heredoc`; `run(flags, body)`; then
`BuilderCalls` for each, then the long methods removed, the compiled-class test and the estate's round trip
unchanged.

## S3 in detail

**What runs.** After the text round trip, every Dockerfile that passed it has its builder class emitted
(`JavaEmitter`, package `roundtrip`, class `D<blob sha>`). All of them compile in one in-memory compilation
(`javax.tools`, sources and class files held in memory, the converter's own class path); a class that does not
compile is reported with its first error and the rest compile again without it. Each class is loaded and its
`dockerfile()` called; it passes when the model it builds equals the conversion's model and that model passes the
text round trip against the original.

**Output.** Each `roundtrip.jsonl` row gains `javaPassed` and, when it failed, `javaDifference`; `coverage.json`
gains `javaRoundTrip: {passed, of}`. `JAVA_ROUND_TRIP=false` skips the check (rows then have no `javaPassed`),
for a quick run; it is on by default. The container's JRE gains `jdk.compiler`, which jdeps cannot see.

**Classes.** `InMemoryCompiler` (sources in, class bytes and per-class errors out, the retry without failures),
`JavaRoundTrip` (emit, compile, load, call, compare; one result per blob), the run wiring in `ConverterRun`.

**Tests, in order**, one assertion each, several in `assertAll`: `InMemoryCompiler` compiles a class to bytes; a
class with an error is named with its message while the others compile; `JavaRoundTrip` passes a real conversion,
fails a model that differs, and reports a class that does not compile; `ConverterRun` writes `javaPassed` and the
coverage count, and leaves them out when the setting is off; `ConverterMain` reads the setting and refuses a bad
value. Measured: the Java pass count equals the text pass count (5,149) on the combined estate; any difference is
a bug in `JavaEmitter` or the builders, fixed test first.

### S3 as built

As designed. On the combined estate every one of the 5,149 Dockerfiles that pass the text round trip has its
class generated, compiled in one in-memory compilation, loaded and run, and every class builds the same model:
`javaRoundTrip: {passed: 5149, of: 5149}`; the whole converter run, compilation included, takes about 16 seconds.
No fault was left for S3 to find: the compiled-class test already caught the one (`Map.ofEntries` losing order)
while S1 was being built.

## S4 in detail

Superseded the same day: the script and `ImageCompareMain` below were replaced by tagged JUnit tests ("Bulk checks"
and "Round-trip tests on any estate"); kept as the record of the first design.

**What is compared.** For each chosen Dockerfile, two images built from the same context at the same commit, minutes
apart on one machine, so on the same base: the original, and the converter's rendering of it (the faithful
conversion, not a golden: the rewrite to policy changes the image by design). The first set is the ten golden
families' representatives (`scripts/compare-images.txt`, their blob SHAs).

**Same image** (question 5): the image config (user, environment, entrypoint, command, working directory, exposed
ports, volumes, stop signal, health check, `ONBUILD`, shell, labels without build-time ones such as
`io.buildah.version` and `org.opencontainers.image.created`) and every file's path, type, mode, owner, group, link
target and SHA-256 of its content; timestamps are never read.

**The script**, `scripts/compare-images.sh`, run where Docker runs (as `lineage-demo.sh`):

1. for each SHA, its repository, commit and path from discovery's manifest; the repository cloned at that commit;
2. the regenerated Dockerfile written by the converter (`ImageCompareMain render`) beside the original;
3. in a Buildah container (native platform, `--format docker` so health checks, `ONBUILD` and `SHELL` are kept): the
   original built with its directory as context, or the repository root when that fails; the regenerated one with
   the same context; for each image its config (`buildah inspect`) and its file listing (`find` and `sha256sum` on
   the mounted root filesystem);
4. `ImageCompareMain compare` on each pair, writing `comparison.jsonl`: per Dockerfile, built or not, the same or
   not, and each difference.

**Java**, in `converter`: `FileListing` (the two listing files to entries by path), `ConfigDifferences` (two configs
to differences, build-time labels ignored), `FileDifferences` (two listings to: only in one, or differing in which
attributes), `ImageComparison` (a pair of image directories to a result), `Regenerated` (a Dockerfile's text to the
converter's rendering, for the target its features need), `ImageCompareMain` (`render`, `compare`).

**Tests, in order**, one assertion each, several in `assertAll`: `FileListing` reads entries and joins their sums;
`ConfigDifferences` finds a changed user, ignores build-time labels, finds an added label; `FileDifferences` finds
a missing file, a changed mode and a changed content, and ignores what question 7 settles; `Regenerated` renders a
real Dockerfile, with heredocs when it has them; `ImageComparison` on two fixture directories; `ImageCompareMain`
writes the line per pair. The script is checked by running it (shellcheck where present); its first real run is
the owner's, as for the lineage demo.

**Question 7.** Some files hold the time they were built in their content, so two builds minutes apart differ
there even when nothing else does: the package databases (`/var/lib/rpm`, `/var/lib/dnf`, `/var/lib/dpkg`,
`/var/lib/apt`), logs (`/var/log`), caches (`/var/cache`, `/root/.cache`, `/etc/ld.so.cache`), Python bytecode
(`*.pyc`), `/tmp`. Ignore their content (still compare that they exist, with mode and owner), or compare them too
and read their differences by hand?

Answered 8 Nov: their content is ignored; that they exist, with type, mode, owner and group, is still compared,
and the output names the ignored patterns.

### S4 as built

As designed, the Java half test first: `FileListing`, `ConfigDifferences`, `FileDifferences`, `Regenerated`,
`ImageComparison` (on hand-written fixtures in the script's layout), `ImageCompareMain` (`STEP=render` or
`compare`), run through Gradle's `:converter:compareImages` so the script uses the project's toolchain.
`scripts/compare-images.sh` checks its syntax and its summary has been run on the fixtures; it has not built an
image here (no Docker in the sandbox). Its first run, on the ten representatives, is the owner's: bases behind a
login (`registry.redhat.io`, `registry.ci.openshift.org`) need `REGISTRY_AUTH_FILE`, and bitnami's build reads a
secret it may refuse to build without; both show as "not built", with the build log beside them.

## Bulk checks as tagged JUnit tests, not scripts

(Named `estateTest` and `imageTest` here; renamed `roundTrip` and `imageRoundTrip` below.)

Asked 8 Nov: no Python or shell scripts; the big checks are JUnit tests under a tag. Today the estate numbers come
from ad-hoc runs and S4's comparison is `scripts/compare-images.sh`; both become tests.

**Tags.** `estate` (needs a discovery directory; `ESTATE_DISCOVERY_DIR`, default `out/all/discovery`) and `images`
(needs Docker and the network). The convention plugin's `test` excludes both, so `check` stays fast and offline;
`./gradlew estateTest` and `./gradlew imageTest` run them. A tagged test whose input is missing is skipped with the
reason (JUnit assumptions), not failed.

**`EstateConversionTest`** (`converter`, `estate`): converts the whole estate with `ConverterRun`, Java round trip
on, and asserts in one `assertAll`: no raw reason (every instruction typed), the text round trip at least the
committed count (`estate/expected.json`: 5,149, raised as fixes land, as the corpus ratchet does), the Java pass
count equal to the text pass count.

**`ImageComparisonTest`** (`converter`, `images`): for each SHA in `estate/compare-images.txt` (the ten golden
families' representatives), builds and compares as the script did, then asserts that every pair that built is the
same, naming each difference. The orchestration moves into Java, in `converter`, tested with a fake builder:

- `Checkouts`: each Dockerfile's repository cloned at its commit and cleaned (`git` through `ProcessBuilder`);
- `Buildah`: one long-lived Buildah container (`docker run -d`, then `docker exec`) for build, inspect, and the
  image's root filesystem as a tar stream (`buildah mount`, then `tar --numeric-owner`), behind an interface;
- `TarListing`: the stream to entries (type, mode, owner, group, link target, SHA-256), with Commons Compress,
  replacing the `find`/`sha256sum` text and `FileListing`;
- `Stubs`: a context's missing sources (from `LocalSources`) as empty files, written on the host;
- `ImageComparisonRun`: per SHA the context order (directory, repository root, directory with stubs), both builds,
  `ImageComparison`, a result per Dockerfile and `comparison.jsonl` under `build/`.

`scripts/compare-images.sh`, its list and `ImageCompareMain`'s render step go; the Gradle task with them. The
other scripts (discovery, combining, OKD selection, the lineage demo, corpus collection, the parser release, the JCR
helpers) are their own slice, planned after this one; the container's entry point stays (answered 8 Nov).

**Tests, in order**, one assertion each, several in `assertAll`: `TarListing` on a tar written in the test;
`Stubs` on a temporary context; `ImageComparisonRun` with a fake `Buildah` (built first try, built from the root,
built on stubs, not built); `Checkouts` and the real `Buildah` only inside the `images` test; the tags and tasks;
`EstateConversionTest`.

### Bulk checks as built

As designed. `estateTest` passes on the combined estate in about 30 seconds. `imageTest` replaces
`scripts/compare-images.sh` and `ImageCompareMain`: the first run of the script (8 Nov) built only s2i-php, read no
config (Buildah's `--format` has no `json`), and showed why the rest did not build: two bases behind a login, three
sources made by CI (now stubbed), one context the script's overwritten log hid (now one log per attempt), one
`apt-get` fetch failure, and one Dockerfile that does not build at its commit. Here (no Docker in the sandbox)
`imageTest` skips with its reason; everything behind the `Builder` interface is tested with a fake.

## Round-trip tests on any estate

Asked 8 Nov: name the bulk tests for what they check, and let them take their examples as input, so a corporate
estate can be pointed at them. Answered: `./gradlew roundTrip` (tag `roundtrip`, `EstateRoundTripTest`: every
Dockerfile of a discovery output, text and Java round trip) and `./gradlew imageRoundTrip` (tag `image-roundtrip`,
`ImageRoundTripTest`: chosen Dockerfiles built twice and the images compared). `RoundTripTest` is taken by
`RoundTrip`'s own test.

**Inputs**, all from the environment, with the public estate as the default:

- `ROUND_TRIP_DISCOVERY_DIR`: any discovery output (default `out/all/discovery`);
- `IMAGE_ROUND_TRIP_DOCKERFILES`: a file, one Dockerfile per line, as its blob SHA or as `owner/repository
  path/to/Dockerfile`, looked up in the manifest; `#` starts a comment (default: the ten representatives);
- `GHE_API_URL` and `GHE_TOKEN` or `GHE_TOKEN_FILE`, as discovery reads them: where the repositories are cloned
  from (default github.com, no token); the token reaches git through its environment (`GIT_CONFIG_*`), never its
  command line or a log;
- `REGISTRY_AUTH_FILE`: an `auth.json` for bases behind a login (a corporate mirror, `registry.redhat.io`),
  mounted into the Buildah container;
- `REGISTRIES_CONF`: a Buildah `registries.conf`, mounted into the container, so public names (`docker.io/...`,
  `quay.io/...`) are pulled through a corporate mirror.

Nothing is published: images are pulled and built into the Buildah container's own storage only, as a corporate
network that allows no pushes to a test registry needs.

Each `RUN --mount=type=secret` id is given an empty secret in both builds (answered 8 Nov), so a Dockerfile that
falls back to a default when its secret is empty builds. The runtime-from-source example becomes Alpine PHP (rank
14, the same family), as Debian's security mirror twice served a package whose sum did not match.

Classes: `CloneSource` (the web host and token from the settings, git's environment), `DockerfileSelection` (the
file's lines to blob SHAs), `LocalSources.secretIds`; `Processes` gains an environment.

### Round-trip tests as built

As planned, each piece test first: `CloneSource`, `DockerfileSelection`, `LocalSources.secretIds`, the empty
secrets in `BuildahContainer`'s build command, the registry mounts in its run command, `Processes` with an
environment. The first `imageRoundTrip`-to-be (8 Nov, 13 minutes) built external-secrets on stubs, then stopped on a
stream the tar reader closed; fixed, and one pair's failure is now that pair's result.

### The first full image round trip (8 Nov)

`./gradlew imageRoundTrip` on the owner's machine, the default list as it was then, 1 hour 30 minutes. Five pairs
built (Alpine PHP, s2i-php, and on stubs rpm-rootfs, the ci-tools binary and alizer's Java service); five did not:
two bases behind a login and one Dockerfile that fails at its commit (all three since replaced in the list), the
tool image (its script is not executable as committed; to be replaced), and bitnami (Debian's mirror served a
package whose sum did not match, as it did for Debian PHP).

Every built pair differed, and every difference is one two builds of the same Dockerfile would also show, not
the converter's: the build container's `Hostname` in the config; `/etc/machine-id`; files generated at build time
(Java's `cacerts`, a self-signed `localhost.crt` and its key); caches (`/@System.solv`, systemd's catalog,
`classes.jsa`); `/tmp/hsperfdata_root/<pid>`, named by a process ID; and PHP's compiled binaries, which embed their
build date.

**Question.** Rather than ignore each such path by name: build the original twice and count only the differences
between the original and the regenerated image that the two originals do not also show (a control build, about
half as much time again)? `Hostname` joins the build-time fields ignored in the config either way.

## Tests, in order (S1; S2-S4 after their questions)

1. `Shell`, `Volume`, `StopSignal`, `Maintainer`: model validation, rendering, converter mapping, emitted Java, round
   trip of a real line from the corpus each.
2. `Expose(List<Port>)`: specs validated (protocol, range, variable), rendering, conversion of several ports.
3. `Copy` options (`--chmod`, `--link`, JSON form), then `CopyHeredoc` with the target's refusal.
4. `Add` with its flags; `add` in the builders refuses a plain local file ("use copy") and accepts a URL or an
   archive.
5. `Run` flags and `RunMount`; the target's refusal of mounts.
6. `RunBody.Exec` and `RunBody.Heredoc`.
7. `Healthcheck` (options, `NONE`) and `Onbuild` (nested instruction).
8. Shell-form `CMD`/`ENTRYPOINT`: `form` in the model and the converter; the builders still exec only; H2 unchanged.
9. The combined estate converted again: typed share, round trip, the corpus.

## Progress

| Step | Typed share | Fully typed files | Round trip |
| --- | --- | --- | --- |
| Before (7 Nov) | 92.6% | 3,206 | 5,149 of 5,250 |
| `SHELL`, `VOLUME` (with its form), `STOPSIGNAL`, `MAINTAINER` | 94.0% | 3,559 | 5,149 |
| `EXPOSE` as `Port` specs (every one typed; a spec that is not a port, such as `${PORT:-80}`, would stay `Raw`) | 94.3% | 3,636 | 5,149 |
| `COPY --chmod`, `--link`, JSON form; `CopyHeredoc` (one plain heredoc; with flags, several or `<<-` stays `Raw`) | 94.5% | 3,691 | 5,149 |
| `ADD` with `--chown`, `--chmod`, `--checksum`, `--keep-git-dir`, `--link`, JSON form; the builders' `add` takes only a URL, a Git repository or a tar archive | 97.7% | 4,193 | 5,149 |
| `RUN` flags as `RunFlag`s (`Mount` with its options as written, `Network`, `Security`) in written order, since a `RUN` step's normal form keeps them; a flagged `RUN` is never merged | 98.2% | 4,538 | 5,149 |
| `RunBody.Exec`; `RunBody.Heredoc(command, delimiter, quoted, lines)`, the command being what precedes the marker (`cat > /etc/x <<'EOF'`, `bash <<'EOF'`; empty for a bare script); several heredocs, `<<-` or a marker that is not last stays `Raw`; H1 reads a heredoc's lines | 98.8% | 4,724 | 5,149 |
| `Healthcheck(options, command, form)` and `NONE`; `Onbuild(instruction)`, built with `asOnbuild()` after the inner call; maps emitted as `OrderedMap.of` so a generated class keeps `ENV`, `LABEL` and `HEALTHCHECK` order (found when the compiled-class test reordered `HEALTHCHECK` options) | 99.1% | 4,754 | 5,149 |

A shell-form `VOLUME` keeps each word as written, quotes included (`Values.written`), found when `VOLUME "/test_dir"`
lost its quotes on the first try.
| `Cmd` and `Entrypoint` with a `CommandForm` (`EXEC`, `SHELL`); the builders' `cmd` and `entrypoint` stay exec, the faithful ones take a form, and H2 finds a typed shell-form entrypoint as it found a raw one | 100% | 5,150 of 5,150 | 5,149 |

### S1 as built

Every instruction of the 5,150 converted Dockerfiles is typed; `coverage.json` lists no raw reason. What would still
be `Raw`, and is not on the combined estate: flags the model lacks (`COPY --exclude`, `--parents`, `RUN --device`,
`--link=false`), several heredocs in one instruction or `<<-`, a heredoc marker followed by more of the command,
`ADD` heredocs, exec forms that are not valid JSON (Docker reads those as shell form), and `ONBUILD` around any of
these.

The round trip is unchanged at 5,149 of 5,250; none of the 101 failures is the model's: 93 Dockerfiles do not parse
(the parse failures and templates), 7 have a malformed digest, and one has a `RUN --mount` with no command, which
Docker refuses as well.

Along the way: a `RUN` step's normal form keeps its flags as written, so `RunFlag`s keep their order and spelling
rather than a canonical one; `Map.ofEntries` in generated classes lost the order of `HEALTHCHECK` options (and of
`ENV` and `LABEL` pairs), so maps are emitted as `OrderedMap.of`; `FileInstructionBuilder`,
`MetadataInstructionBuilder`, `InstructionParts` and `BuilderCalls` split classes that grew past 150 lines.

