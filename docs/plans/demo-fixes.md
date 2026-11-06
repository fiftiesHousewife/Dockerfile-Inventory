# Fixes from the sclorg demonstration: plan

Status: all six fixed 6 Nov; the demonstration is rerun from a fresh discovery. The first run on real public data (sclorg: 75 repositories, 987 Dockerfiles) showed six
problems. Each is fixed test-first, using trimmed real sclorg lines as fixtures where they exist; the demonstration
is then rerun from a fresh discovery so the numbers can be compared.

| # | Problem seen | Fix | Module |
| --- | --- | --- | --- |
| F1 | Discovery took files that are not Dockerfiles: `dockerfile.go`, an editor's `Dockerfile.tmPreferences`, `.tpl` and Jinja templates, and Docker's own parser test files under `vendor/` | A Dockerfile path must not end in a source, document or template extension (`.go`, `.md`, `.txt`, `.json`, `.yaml`, `.xml`, `.tmPreferences`, `.tpl`, `.template`, `.j2`, `.jinja`, `.erb`, `.in`), and is not under a third-party copy (`vendor/`, `node_modules/`, `third_party/`) | `discovery` |
| F2 | Two `rewrite-docker` bugs reject valid Dockerfiles: an apostrophe in a comment line inside a continued `RUN`, and a `# key=value` comment after the first instruction (read as a parser directive) | After a syntax error only, parse again with whole-line comments blanked, as Docker drops them, keeping line numbers. The leading comment block (parser directives) and heredoc bodies are left alone. Draft both upstream issues with the real lines | `facts` |
| F3 | S2 flagged `VALKEY_PREFIX` and `VALKEY_CONF`: `KEY` matched inside `VALKEY` | Match names by their parts (split on `_`, `-`, `.`): `KEY` only as a whole part; the longer words (`PASSWORD`, `SECRET`, `TOKEN`...) inside a part, so `DBPASSWORD` still counts | `rules` |
| F4 | The round trip was stricter than Docker: `WORKDIR "$HOME"` against `$HOME`, `CMD [ "x" ]` against `CMD ["x"]`, one `ENV` setting a key twice; plus some lost labels and 2 models that converted back differently | Compare a quoted single value unquoted, exec forms as parsed JSON arrays, `ENV` and `LABEL` keys as sets. Find and fix the lost-label and model cases in the converter | `converter` |
| F5 | `$INSTALL_PKGS` counted as a package name, so most sclorg signatures could not tell their real package sets apart | Expand a package word that is exactly `$NAME` or `${NAME}` from the same `RUN`'s assignments (`INSTALL_PKGS="a b" && yum install $INSTALL_PKGS`) or the stage's `ARG` and `ENV`, splitting on whitespace; an unknown variable stays as written | `facts` |
| F6 | A relative `OUT_DIR` landed in the module's folder, as Gradle's `run` starts there; a parse-failure message named nothing when the merged line followed a blank line | Every `run` task starts in the repository root; the message names the first non-blank line after the bare newline | `build-logic`, `facts` |

## Tests, in order

F1. 1. `dockerfile.go`, `Dockerfile.tmPreferences`, `Dockerfile.tpl`, `Dockerfile.j2`, `README.Dockerfile.md` are not
    Dockerfiles; `Dockerfile.rhel9`, `Dockerfile.c10s`, `app.Dockerfile`, `Containerfile` still are.
    2. Anything under `vendor/`, `node_modules/` or `third_party/` is not, at any depth.
F2. 3. The real s2i-python lines (an apostrophe in a comment inside a continued `RUN`) parse, with the same steps
    as without the comments.
    4. The real nginx lines (comment block with `UID=1001 && GID=0`) parse.
    5. Line numbers after the blanked comments are unchanged; a `# syntax=` directive and heredoc comments are kept.
    6. A file that parses first time keeps its comments (the converter carries them).
F3. 7. `VALKEY_PREFIX`, `KEYCLOAK_URL`, `MONKEY` are not secret names; `API_KEY`, `KEY`, `DB_PASSWORD`,
    `DBPASSWORD`, `GITHUB_TOKEN`, `client-secret` are.
F4. 8. `WORKDIR "$HOME"`, `CMD [ "x" ]` and an `ENV` setting a key twice pass the round trip.
    9. The lost-label and different-model cases, reproduced from their sclorg files, pass or fail for a real reason.
F5. 10. `INSTALL_PKGS="a b" && yum install -y $INSTALL_PKGS` gives packages `a`, `b`.
    11. A package variable from the stage's `ENV` or `ARG` is expanded; an unknown one stays as `$NAME`.
F6. 12. The glued-line message names the first non-blank line.
    13. `run` tasks' working directory is the repository root (checked by running a stage with a relative `OUT_DIR`).

## As built

- F2: the repro narrowed the second parser bug to any `# key=value` comment after the first instruction (the
  trailing backslash was a red herring; `# syntax=` there is the same bug). A blanked comment inside a continued
  instruction becomes a lone backslash, since an empty line there would end the instruction for the parser. The
  fallback also rescues the old "`# syntax=` after an instruction" case, so that test now checks it parses.
  Six upstream drafts are in `docs/upstream/rewrite-docker-issues.md`.
- F4: three converter bugs behind the label differences: a tab after `LABEL` (the keyword was split on a space),
  comment lines inside a continued `LABEL` read as keys, and the legacy `LABEL key value` form read as two keys.
- F6: `facts` on the old discovery output now parses 818 of 893 files (785 before), from F2 alone.

## Second round, from the rerun (6 Nov)

| # | Problem seen | Fix | Module |
| --- | --- | --- | --- |
| F7 | A package named `\`: inside double quotes, `INSTALL_PKGS="a b \<newline> c"` kept the backslash-newline, which the shell removes | The tokeniser removes backslash-newline inside double quotes too | `facts` |
| F8 | `LABEL MAINTAINER "Marek Polacek <...>"` (the legacy form) read as several keys in facts' steps, failing 12 round trips and skewing lineage's matching | Facts' steps read legacy `LABEL key value` as one key, as they already do for `ENV` | `facts` |
| F9 | `dockerfile.py` still collected | Add source extensions: `py`, `sh`, `bash`, `rb`, `js`, `ts`, `java`, `kt`, `groovy`, `ps1` | `discovery` |
| F10 | Jinja templates saved as `Dockerfile` (`{% if %}`, `{{ spec.version }}`) are most of the remaining parse failures | A file that fails to parse and holds template markers is recorded as a template; facts' and the report's failure rates leave templates out and count them separately. A file that parses is never called a template (`docker inspect --format '{{.Id}}'` in a `RUN` is fine) | `facts`, `report` |
| F11 | One thermostat file converts back to a different model; two files hit `InMemoryDiffEntry` failing to initialise inside OpenRewrite | Find each cause; fix ours, draft upstream what is theirs | `converter`, docs |

Tests, in order: 14. a double-quoted value continued over lines has no backslash words; 15. legacy `LABEL` steps
give one key; 16. the source extensions are not Dockerfiles; 17. a failing file with `{%` or `{{ ... }}` is a
template, one that parses with `{{.Id}}` is not; 18. the rates count templates apart; 19. the thermostat case.

As built (second round): the `InMemoryDiffEntry` failure was ours. OpenRewrite reports a file it cannot print back
through its bundled JGit, which needs `slf4j-api` at runtime; `facts`, `rules` and `converter` now carry it, and
the adapter treats the `ParseError` tree the parser then returns as a failure with its own message. The thermostat
case was a backslash followed by a space before the newline, which Docker reads as a continuation; the converter,
facts' steps, the merged-line check and rule S3 all now accept trailing spaces or tabs after the backslash.
Lineage's history normaliser reads the legacy `LABEL key value` form as facts now does, keeping the contract.

## Third round (6 Nov)

| # | Problem seen | Fix | Module |
| --- | --- | --- | --- |
| F12 | Three symlinks fetched as Dockerfiles: git stores a symlink as a blob holding its target path (`../../../Dockerfile.fedora`) | Tree entries keep their mode; an entry with mode `120000` (a symlink) is not a Dockerfile | `discovery` |
| F13 | `postgresql-container` `13/Dockerfile.rhel9` rejected at line 46 (`extraneous input '='`) | Find the cause; fix ours, draft upstream what is the parser's | `facts` or docs |

Tests: 20. a symlink entry named `Dockerfile` is not collected, and the REST tree's `mode` is read; 21. the
postgresql case, from its real lines.
