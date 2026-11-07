# Completing the DSL: plan

Status: planned 7 Nov; questions answered 7 Nov; building S1.

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
| `RUN` with flags | 433 | `Run(body, mounts, network, security)` with `RunMount(type, options)` | `RUN --mount=type=cache,target=/c ...`; refused unless the target allows mounts |
| `SHELL` | 346 | `Shell(command)` | `SHELL ["/bin/bash", "-c"]` |
| shell-form `CMD` | 337 | `Cmd(command, form)`, form exec or shell (question 1) | `CMD node server.js` |
| `VOLUME` | 304 | `Volume(paths)` | `VOLUME ["/data"]` |
| `RUN` exec form, heredoc | 196 | `RunBody.Exec(arguments)`, `RunBody.Heredoc(delimiter, quoted, lines)` | `RUN ["a", "b"]`; `RUN <<EOF ... EOF`, refused unless the target allows heredocs |
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

A shell-form `VOLUME` keeps each word as written, quotes included (`Values.written`), found when `VOLUME "/test_dir"`
lost its quotes on the first try.

