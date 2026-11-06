# Fixes from the openshift run: plan

Status: S1-S3 built 6 Nov (unreadable RUNs 663 -> 162); P1 built; P2 next.

`openshift` (862 repositories, 3,180 locations, 2,833 files): 97 parse failures besides 32 templates (3.4%), 663 of
6,019 shell-form `RUN`s too complex to read (10.7%), and 230 files failing the round trip. Fixed test-first, using
trimmed real openshift lines as fixtures, as in `demo-fixes.md`; the run is repeated after each group of fixes.

## Shell reading first (the owner's choice)

What makes the 663 unreadable, counted by a probe over every openshift `RUN` (a `RUN` can show several):
`$( )` 456 (the only cause in 280), `if` 178 (76), `( )` group or subshell 89 (18), `for`/`while` 50 (20), backticks
34 (28), a pipe into a shell 34 (27), `case` 28 (9).

| # | Problem | Fix | Module |
| --- | --- | --- | --- |
| S1 | A command substitution makes the whole `RUN` unreadable, when usually one word is unknown (`ARCH=$(uname -m) && curl .../$ARCH.tar.gz`) | `$( ... )` and backticks, nested or quoted, become one word whose value is unknown; the commands around them are read. A substitution's own commands are not counted as the `RUN`'s | `facts` |
| S2 | An escaped quote inside double quotes (`"a \"b\" c"`) ends the quoted text early | Inside double quotes, `\"`, `\\`, `` \` `` and `\$` are escapes, as in the shell | `facts` |
| S3 | `if`/`then`/`else`/`fi` makes the `RUN` unreadable | Answered 6 Nov: count every branch's commands, so a package installed on one branch counts as one this Dockerfile can install. The keywords `if`, `then`, `elif`, `else`, `fi` are dropped and the commands they introduce are read; the condition is read as a command too | `facts` |
| S4 | `for`/`while`, groups, `case` | After S1-S3, measured again; planned then | `facts` |

Tests, in order (S1, S2): 1. `ARCH=$(uname -m) && curl -o x https://h/$ARCH.tgz` gives two commands, the
assignment holding an unknown value; 2. a substitution inside double quotes, nested substitutions, and backticks
read the same way; 3. `dnf install -y $(cat pkgs.txt)` counts no package from the substitution and does not guess;
4. an unclosed `$(` still makes the `RUN` unreadable; 5. `"a \"b\" c"` is one word, `a "b" c`; 6. the real openshift
lines behind the two unclassified probe cases read.

## Then the rest of the run

| # | Problem seen | Next step |
| --- | --- | --- |
| P1 | 23 files: rewrite-docker's "is not print idempotent" | Done 6 Nov: two upstream bugs (issues 8, 9): whitespace before a comma in a JSON array (11 files), and `${NAME:-default}` in a flag value (12). The retry drops the whitespace and writes `${NAME-default}` in flags |
| P2 | 8 `extraneous input 'TARGETARCH'`, 7 `mismatched input 'CMD'`, 4 heredoc `<<EOF`, 3 `token recognition error at '\'`, and smaller groups | Done 6 Nov, in part: `ARG A B` is upstream issue 10, worked around in the retry (`ARG A=$A B=$B`); a heredoc after a command in `RUN` is issue 11, no safe workaround; discovery no longer takes `*.dockerignore`. Correctly rejected, left as failures: `ENV PATH` with no value, a bare `HEALTHCHECK`, `CROSS_BUILD_COPY` (a Kubernetes test-image preprocessor). Open: Docker's own parser test inputs |
| C1 | 77 round trips: "the target does not support" heredocs or `RUN --mount` | The round trip renders for a target that allows what the original uses |
| C2 | 10 digests that are not `sha256:` and 64 hex characters | Find what they are (a variable, another algorithm) |
| C3 | 10 conversions convert back to a different model; `USER $UID:$GID` becomes `${UID}:${GID}` | Find each cause; compare `$X` and `${X}` as the same |
