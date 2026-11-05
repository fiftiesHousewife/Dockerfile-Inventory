# Phase 1 — Parse and normalise: plan

Status: all six slices built (5-6 Nov). The gate (rates published) waits on discovery output from the pilot. Slices below are built test-first in order, each committed when `check` is green.

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
- `Arg`, `Env` (pairs), `User` (user, group), `Expose` (`Port`s), `Entrypoint`, `Cmd`, `Onbuild`.
- There is no shell AST: a shell-form `RUN` is one string, with backslash-newline continuations left in.

Found by probing (slice 1), not documented:

- Syntax errors do not throw and add no marker: they go to the `ExecutionContext` error handler as
  `DockerParsingException`, and a tree is still returned. A file with no `FROM` is such an error.
- **A line the grammar does not recognise (`RUNN echo`) is silently appended to the previous instruction's last
  argument**, e.g. the `FROM` image name becomes `ubi9\nRUNN echo typo`. `FactsParser` treats any literal holding a
  newline not preceded by a backslash as a parse failure. Worth an upstream issue.
- The `# syntax=` parser directive is not visible in the tree; the extractor reads it from the text.
- **A `# syntax=` line after the first instruction is rejected as a syntax error**, though Docker reads it as an
  ordinary comment, so a valid Dockerfile fails to parse. A test records it, to delete when fixed upstream.

## Decisions taken without asking (say if any is wrong)

| # | Decision | Why |
| --- | --- | --- |
| D1 | Import `org.openrewrite:rewrite-bom` 8.90.4 (latest, 24 Aug 2026) instead of the recipe BOM, whose rewrite is 8.83.0 | Only core modules are needed; newest parser fixes |
| D2 | Only `FactsParser` and `FactExtractor` import `org.openrewrite`; everything after them works on our own records | `rewrite-docker` APIs are marked unstable |
| D3 | Facts describe the final stage (the image that ships); package calls are kept for every stage with the stage index; the signature uses final-stage packages | Builder stages do not reach the image |
| D4 | Base family = last path segment of the image name (`registry/ubi9/ubi-minimal` gives `ubi-minimal`); major = leading digits of the tag (`9.4-1227` gives `9`) | Simple, explainable; refined once Phase 2 shows real names |
| D5 | User kind: `root` when there is no `USER`, or it is `root` or `0`; `numeric` for other digits; `named` otherwise; `unresolved` when it holds a variable ARG cannot resolve | Phase 3's arbitrary-UID rule needs numeric versus named |
| D6 | A final stage `FROM <earlier stage>` takes that stage's base | Multi-stage chains end on a real image |
| D7 | Heredoc and `RUN --mount` are counted as used; whether they build is not knowable from source, so "evidence Buildah supports it" is reported as usage only | The Decisions table assumed more than source shows |
| D8 | Unresolved `FROM` is counted per organisation using `manifest.jsonl` | Organisations are the only grouping left |
| D9 | No healthcheck fact (decided by you, 5 Nov) | OpenShift ignores `HEALTHCHECK`; its rule was dropped |

## Slice 1 — Module and parser adapter

`facts` module; `FactsParser.parse(blobSha, text)` returns a parsed `Docker.File` or a `ParseFailure(sha, message)`.

1. A minimal `FROM ubi9/ubi-minimal` parses to a file with one stage.
2. Text the grammar rejects becomes a `Failed` result carrying the message, not an exception.
3. A line glued onto the previous argument is a failure; continuations and heredocs are not.
(Planned "many inputs in one call" dropped: the adapter parses one blob per call.)

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
8. Comments (`#` at a word start) end the line; an unescaped newline separates commands.

As built, beyond the list: redirections (`> /dev/null`, `2>&1`, `>>file`) are dropped with their targets, so
they never look like package names; a quoted word is never taken for a redirection; an unclosed quote and
command substitution inside double quotes are opaque. Note for Phase 3: a pipe into a shell makes the body
opaque, so rule S3 (`curl | sh`) must match the raw `RUN` text, not tokenised commands.

## Slice 3 — Package calls

`PackageCalls.of(commands)` returns `PackageCall(manager, subcommand, flags, packages, cleanedInSameRun)`.

1. `microdnf install -y git tar` gives manager `microdnf`, subcommand `install`, flags `[-y]`, packages `[git, tar]`.
2. The same for `dnf`, `yum`, `apk add`, `apt-get install`, `apt install`.
3. `--flag=value` stays one flag; `--nodocs`, `--setopt=install_weak_deps=0` are flags.
4. Clean in the same RUN: `<mgr> clean all`, `rm -rf /var/cache/{dnf,yum}`, `rm -rf /var/lib/apt/lists/*`;
   `apk add --no-cache` counts as clean.
5. Non-install calls (`dnf update`, `microdnf upgrade`) are recorded with no packages.
6. Unknown commands produce no calls.

As built: a clean counts only after the install and within the manager's family (`dnf`, `yum`, `microdnf`
share the RPM caches); `apt-get clean` does not count, as it leaves `/var/lib/apt/lists`; packages are recorded
only for `install`, `add` and `reinstall`. Test 3 (flags with `=`) held by construction and is covered by the
flag cases in test 2.

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
9. BuildKit features: `heredoc`, `run-mount`, `syntax-directive`.

As built: the parser splits name, tag and digest before variables are substituted, so the extractor rebuilds the
reference, substitutes, and splits it itself (a tag carried in an ARG default; a registry port is not a tag).
Inside a stage, variables come from in-stage `ARG` (a bare `ARG NAME` takes the global default) and `ENV`; user,
entrypoint and exposed ports carry over to a stage built `FROM` an earlier stage. `/tcp` is dropped from ports
as the default protocol. Clean calls (`microdnf clean all`) appear in the package calls as calls themselves.

## Slice 5 — Normal forms

1. Text-normal: comments dropped, continuations joined, whitespace collapsed, keywords upper-cased; two files
   differing only in those ways hash equal.
2. Signature (D3, D4, D5): equal for functionally equal files with different package order, different for a
   different package set or user kind.

As built: the signature is a readable `|`-joined string (hashed for grouping), so a surprising cluster can be
explained. Packages come from final-stage calls that name packages; versions written into package names are
kept, so `git` and `git-2.43.5` differ. Text-normal treats heredoc body lines like instructions, which can only
merge files whose bodies differ in comments.

## Slice 6 — Facts run

`Facts.run(discoveryDir, outDir)`: every blob not yet in `facts.jsonl` or `parse-failures.jsonl` is parsed and
extracted; rows are appended so a rerun resumes. Writes `rates.json`: blobs, parse failures and their rate,
RUNs and opaque RUNs and their rate, unresolved `FROM` per organisation (D8), BuildKit feature counts (D7).

1. Facts for every blob, failures recorded.
2. Rerun parses nothing new.
3. Rates computed from the rows.
4. `FactsMain` from the environment (`DISCOVERY_DIR`, `OUT_DIR`).

As built: discovery's unfinished `*.part` blobs are skipped; `rates.json` is rewritten from every row each run;
unresolved `FROM` counts distinct blobs per organisation. Checked end to end with `./gradlew :facts:run` on a
hand-made discovery directory. `FactsMain` is untested, like `DiscoveryMain`.

## Open questions (not blocking)

- Is the D4 family and major rule good enough for your registry's naming? Phase 2's base catalogue will show.
- Fixture Dockerfiles are written to look like UBI estate files; swap in anonymised real ones after the pilot.
