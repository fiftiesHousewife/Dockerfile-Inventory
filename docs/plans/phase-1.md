# Phase 1 — Parse and normalise: plan

Status: planned 5 Nov; slices below are built test-first in order, each committed when `check` is green.

Phase 1 parses each unique Dockerfile blob once with `rewrite-docker`, reduces it to an `ImageFacts` record and
two normal-form hashes, and publishes the parse-failure and opaque-`RUN` rates. Module: `facts`. Input: the
discovery output directory (`blobs/`, `manifest.jsonl`). It reads those files and does not depend on the
`discovery` module.

## What `rewrite-docker` 8.90.4 gives (checked against the jar)

- `DockerParser.builder().build()`, `parseInputs(Iterable<Parser.Input>, Path, ExecutionContext)`; `accept(Path)`
  decides by file name, so inputs are named `<sha>/Dockerfile`.
- `Docker.File`: `getGlobalArgs()` (ARGs before the first FROM), `getStages()`.
- `Docker.Stage`: `getFrom()`, `getInstructions()`.
- `Docker.From`: `getImageName()`, `getTag()`, `getDigest()` as `Argument`s, `getAs()` for the stage name, `getFlags()`.
- `Docker.Argument.getContents()`: `Literal` (text) and `EnvironmentVariable` (`${NAME}`) parts, which is
  what ARG substitution needs.
- `Docker.Run.getCommand()`: `ShellForm` (one `Literal`), `ExecForm` (`List<Literal>`), `HeredocForm`; `getFlags()`
  carries `--mount`.
- `Arg`, `Env` (pairs), `User` (user, group), `Expose` (`Port`s), `Entrypoint`, `Cmd`, `Healthcheck`, `Onbuild`.
- There is no shell AST: a shell-form `RUN` is one string.

## Decisions taken without asking (say if any is wrong)

| # | Decision | Why |
| --- | --- | --- |
| D1 | Import `org.openrewrite:rewrite-bom` 8.90.4 (latest, 24 Aug 2026) instead of the recipe BOM, whose rewrite is 8.83.0 | Only core modules are needed; newest parser fixes |
| D2 | Only `FactsParser`, the adapter, imports `org.openrewrite`; everything after it works on our own records | `rewrite-docker` APIs are marked unstable |
| D3 | Facts describe the final stage (the image that ships); package calls are kept for every stage with the stage index; the signature uses final-stage packages | Builder stages do not reach the image |
| D4 | Base family = last path segment of the image name (`registry/ubi9/ubi-minimal` gives `ubi-minimal`); major = leading digits of the tag (`9.4-1227` gives `9`) | Simple, explainable; refined once Phase 2 shows real names |
| D5 | User kind: `root` when there is no `USER`, or it is `root` or `0`; `numeric` for other digits; `named` otherwise; `unresolved` when it holds a variable ARG cannot resolve | Phase 3's arbitrary-UID rule needs numeric versus named |
| D6 | A final stage `FROM <earlier stage>` takes that stage's base | Multi-stage chains end on a real image |
| D7 | Heredoc and `RUN --mount` are counted as used; whether they build is not knowable from source, so "evidence Buildah supports it" is reported as usage only | The Decisions table assumed more than source shows |
| D8 | Unresolved `FROM` is counted per organisation using `manifest.jsonl` | Organisations are the only grouping left |

## Slice 1 — Module and parser adapter

`facts` module; `FactsParser.parse(blobSha, text)` returns a parsed `Docker.File` or a `ParseFailure(sha, message)`.

1. A minimal `FROM ubi9/ubi-minimal` parses to a file with one stage.
2. Text the grammar rejects becomes a `ParseFailure` carrying the message, not an exception.
3. Many inputs in one call keep each result tied to its blob SHA.

## Slice 2 — Shell tokeniser

`ShellTokeniser.parse(script)` returns the simple commands (word lists) of a `RUN` body, or empty (opaque).
Pure Java; no OpenRewrite.

1. One command splits into words.
2. `&&`, `||`, `;` separate commands.
3. Single and double quotes group words and are removed.
4. Backslash-newline continuations join lines.
5. Leading `NAME=value` assignments are dropped from a command.
6. Opaque: `if`, `case`, `for`, `while`, `$( )`, backticks, `( )` subshells, `{ }` groups, function definitions,
   heredocs (`<<`), pipes into a shell (`| sh`, `| bash`).
7. Pipes between other commands split like `;` (each side is a command).
8. Comments (`#` at a word start) end the line.

## Slice 3 — Package calls

`PackageCalls.of(commands)` returns `PackageCall(manager, subcommand, flags, packages, cleanedInSameRun)`.

1. `microdnf install -y git tar` gives manager `microdnf`, subcommand `install`, flags `[-y]`, packages `[git, tar]`.
2. The same for `dnf`, `yum`, `apk add`, `apt-get install`, `apt install`.
3. `--flag=value` stays one flag; `--nodocs`, `--setopt=install_weak_deps=0` are flags.
4. Clean in the same RUN: `<mgr> clean all`, `rm -rf /var/cache/{dnf,yum}`, `rm -rf /var/lib/apt/lists/*`;
   `apk add --no-cache` counts as clean.
5. Non-install calls (`dnf update`, `microdnf upgrade`) are recorded with no packages.
6. Unknown commands produce no calls.

## Slice 4 — Fact extraction

`FactExtractor.extract(Docker.File)` returns `ImageFacts`.

1. Stage count; final base, tag, digest pinned for a plain `FROM`.
2. Global `ARG` defaults substitute into `FROM ${BASE}:${TAG}`; an ARG with no default leaves the base unresolved.
3. D6: final stage from an earlier stage takes its base.
4. Package calls per stage from shell-form RUNs; exec-form RUNs are taken as one command.
5. A RUN the tokeniser declines counts as opaque.
6. Final user and user kind (D5), including `USER ${APP_UID}` with an ARG default.
7. Exposed ports, sorted and deduplicated.
8. Entrypoint form: `shell`, `exec` or `none`, from the last `ENTRYPOINT` of the final stage.
9. Healthcheck present (not `HEALTHCHECK NONE`).
10. BuildKit features: `heredoc`, `run-mount`, `syntax-directive`.

## Slice 5 — Normal forms

1. Text-normal: comments dropped, continuations joined, whitespace collapsed, keywords upper-cased; two files
   differing only in those ways hash equal.
2. Signature (D3, D4, D5): equal for functionally equal files with different package order, different for a
   different package set or user kind.

## Slice 6 — Facts run

`Facts.run(discoveryDir, outDir)`: every blob not yet in `facts.jsonl` or `parse-failures.jsonl` is parsed and
extracted; rows are appended so a rerun resumes. Writes `rates.json`: blobs, parse failures and their rate,
RUNs and opaque RUNs and their rate, unresolved `FROM` per organisation (D8), BuildKit feature counts (D7).

1. Facts for every blob, failures recorded.
2. Rerun parses nothing new.
3. Rates computed from the rows.
4. `FactsMain` from the environment (`DISCOVERY_DIR`, `OUT_DIR`).

## Open questions (not blocking)

- Is the D4 family and major rule good enough for your registry's naming? Phase 2's base catalogue will show.
- Fixture Dockerfiles are written to look like UBI estate files; swap in anonymised real ones after the pilot.
