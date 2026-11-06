# Fixes from the sclorg demonstration: plan

Status: planned 6 Nov. The first run on real public data (sclorg: 75 repositories, 987 Dockerfiles) showed six
problems. Each is fixed test-first, using trimmed real sclorg lines as fixtures where they exist; the demonstration
is then rerun from a fresh discovery so the numbers can be compared.

| # | Problem seen | Fix | Module |
| --- | --- | --- | --- |
| F1 | Discovery took files that are not Dockerfiles: `dockerfile.go`, an editor's `Dockerfile.tmPreferences`, `.tpl` and Jinja templates, and Docker's own parser test files under `vendor/` | A Dockerfile path must not end in a source, document or template extension (`.go`, `.md`, `.txt`, `.json`, `.yaml`, `.xml`, `.tmPreferences`, `.tpl`, `.template`, `.j2`, `.jinja`, `.erb`, `.in`), and is not under a third-party copy (`vendor/`, `node_modules/`, `third_party/`) | `discovery` |
| F2 | Two `rewrite-docker` bugs reject valid Dockerfiles: an apostrophe in a comment line inside a continued `RUN`, and a block of comment lines holding `=` and `&&` | After a syntax error only, parse again with whole-line comments blanked, as Docker drops them, keeping line numbers. The leading comment block (parser directives) and heredoc bodies are left alone. Draft both upstream issues with the real lines | `facts` |
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
