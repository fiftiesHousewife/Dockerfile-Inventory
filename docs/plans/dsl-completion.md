# Completing the DSL: plan

Status: planned 7 Nov; questions answered 7 Nov; S1 done 7 Nov (every instruction on the combined estate typed); S2 done 8 Nov (ten goldens, one per family); S3 next.

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

