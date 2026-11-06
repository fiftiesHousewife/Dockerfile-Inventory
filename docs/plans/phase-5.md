# Phase 5 — Converter: plan

Status: built 6 Nov; Phase 5b (building and comparing images, PRs) waits on Buildah and Phase 4b. Slices are built test-first, as in the earlier phases.

Phase 5 maps a parsed Dockerfile to the Phase 4 model and emits a generated builder class; no image migrates
unless it passes the round-trip check. Module: `converter`, which may import OpenRewrite and depends on `facts`
(parser, tokeniser, steps) and `dsl` (model, builder, renderer). Teams opt in. Building the original and the
converted image and comparing them (the delivery plan's semantic verification) needs Buildah and pinned bases,
so it is Phase 5b; so is delivering PRs, which needs Phase 4b's job.

## Answers (6 Nov)

| # | Question | Answer | What it changes |
| --- | --- | --- | --- |
| Q1 | What a generated class is | Faithful first | The class reproduces the original through the builder without policy checks; each breach becomes a `// TODO(<rule>)` comment. Fixes come later as separate PRs |
| Q2 | Install flags | Keep the original flags | `PackageInstall` gains an optional flag list; a converted install renders exactly its original flags |
| Q3 | Emitting Java | JavaPoet (`com.palantir.javapoet:javapoet` 0.22.0) | Source built as a syntax tree, so literals and comments are always escaped correctly |

## Decisions taken without asking (say if any is wrong)

| # | Decision | Why |
| --- | --- | --- |
| D1 | A `RUN` becomes a typed `Install` only when it is exactly one install and its manager's `clean all` (or `apk add --no-cache`, or apt's install and list removal), with nothing else; any other `RUN` is a `Shell` holding the original command text, continuations joined | The delivery plan: typed only when fully understood. Keeping the text keeps the image the same |
| D2 | `ADD`, `COPY --chmod`/`--link`, `EXPOSE` with a protocol, range or variable, shell-form `ENTRYPOINT` and `CMD`, and `HEALTHCHECK`, `VOLUME`, `STOPSIGNAL`, `SHELL`, `ONBUILD` become `Raw`, each with its reason | The model types none of these, and converting shell-form entry points changes signal handling |
| D3 | The round-trip check compares the original and the rendered conversion through facts' normal-form steps, stage by stage, with each stage's `FROM`; inside a typed install, flag and package order are ignored | Comments, whitespace and continuation layout are not behaviour; the order of installed packages is not either |
| D4 | The builder gains a faithful path: `model()` returns the Dockerfile as written, with no policy checks and none of the policy's additions (group-0 step, `uid:0` ownership); `check(policy)` lists the breaches without throwing | A faithful class must not change the image (Q1) |
| D5 | Labels are read from the instruction's text, not the parsed pairs, as in Phase 3 | rewrite-docker folds `LABEL a=$X b=$Y` into one pair |

## Slices

1. **dsl: faithful building.** `PackageInstall` flags (Q2); builder methods for what existing files hold (global
   `ARG`s with defaults, named final stages, a stage built from an earlier stage, `COPY` with several sources,
   `--from` and `--chown`, multi-pair `ENV` and `LABEL`); `model()` and `check(policy)` (D4).
   1. An install with flags renders exactly those flags, then the manager's clean.
   2. `model()` returns what was written: no group-0 step, no `uid:0` ownership, and no exception for a breach.
   3. `check(policy)` lists the same breaches `build(policy)` would throw.
   4. The new builder methods render as their instructions.
2. **LST to model.** `Convert.of(Docker.File)` gives the model and a list of `Raw` uses with reasons.
   5. `FROM` with tag, digest and stage name; a stage built from an earlier stage; global `ARG`s.
   6. `ARG`, `ENV`, `LABEL`, `WORKDIR`, `USER`, `EXPOSE`, exec-form `ENTRYPOINT` and `CMD` become typed.
   7. A `RUN` that is only an install and its clean becomes an `Install` with its flags; any other `RUN` a `Shell`
      with the original text (D1).
   8. `COPY` with `--from` and `--chown`; `ADD` and other flags become `Raw` (D2).
   9. Shell-form `ENTRYPOINT` and `CMD`, and every untyped instruction, become `Raw` with a reason (D2).
3. **Round trip.** `RoundTrip.check(original, model)`: pass, or the first difference.
   10. The real python 3.11 Dockerfile (Phase 1b's contract) converts and passes.
   11. A conversion that drops or changes an instruction fails, naming the step.
   12. Package and flag order inside a typed install do not count (D3).
4. **Emit Java.** `JavaEmitter.emit(name, model, comments, breaches)` gives one class per image, with
   `public static Dockerfile dockerfile()` built through the builder's faithful path.
   13. The emitted class compiles and its `dockerfile()` renders a Dockerfile that passes the round trip.
   14. Comments in the original become `//` lines before their instruction.
   15. Each policy breach becomes a `// TODO(<rule>): <message>` line at the top of the class.
5. **Run and coverage.** `ConverterRun`: for each blob, convert, check the round trip, and for passes write
   `generated/<Name>.java`; write `roundtrip.jsonl` and `coverage.json` (typed instructions over all instructions
   per image, and an estate-wide histogram of `Raw` reasons, the builder's backlog). `ConverterMain` from the
   environment. Untested, like the other mains.
   16. Passing blobs get a class; failing ones get a `roundtrip.jsonl` row with the difference and no class.
   17. Coverage and the reasons histogram are computed from the conversions.

   As built: the dsl gained `ImageBuilder.of(model)` and `arg(name, default)`, so breaches can be listed for any
   conversion. `ENV` values come from the parsed pairs; labels from the text (D5). The emitter's test compiles
   the generated source with the JDK's compiler, loads it and checks its model and round trip.

   Found by running it end to end on the real python 3.11 Dockerfile (passes, 92% typed): JavaPoet's file comment
   is a format string, so a breach message holding `$INSTALL_PKGS` crashed the emitter; and backslash-newline
   continuations inside quoted `ENV` and `LABEL` values were kept, where Docker removes them. The round trip did
   not catch the second, because it compares `ENV` and `LABEL` by key and the converter misread both sides the
   same way. A conversion that misreads consistently can only be caught by building and comparing the images,
   which is why Phase 5b's semantic verification stays a gate.
