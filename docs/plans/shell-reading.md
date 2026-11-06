# Reading more shell in RUN (S4): plan

Status: planned 6 Nov; questions answered 6 Nov; S4a built, S4b next.

`docker-library` (the official images) has 870 of 2,492 shell-form `RUN`s too complex to read (35%); openshift
2.7%, konflux-ci 2.9%, redhat-developer 4.7%. An unreadable `RUN` counts no packages, so the inventory undercounts
exactly where official images put their installs. S1-S3 (`openshift-fixes.md`) taught the reader command
substitutions and `if`; this teaches it the rest that real Dockerfiles use.

What makes docker-library's 870 unreadable (a `RUN` can show several; in brackets, the only cause):

| Construct | RUNs | Only cause |
| --- | --- | --- |
| `{ ...; }` group | 307 | 223 |
| `for` / `while` / `until` | 338 | 167 |
| `case` | 264 | 183 |
| function | 59 | 0 |
| `( ... )` subshell | 54 | 12 |
| pipe into a shell | 6 | 2 |
| other: PowerShell (Windows images), and long PHP-style scripts not yet classified | 92 | 92 |

## Design

| # | Construct | Read as | Module |
| --- | --- | --- | --- |
| S4a | `{ list; }` and `( list )` | Their commands, the braces and parentheses dropped; a redirection after the group applies to it and is dropped like any other | `facts` |
| S4b | `case WORD in p) cmds ;; ... esac` | Every branch's commands, like `if` (the owner's choice for S3) | `facts` |
| S4c | `for` / `while` / `until` | The body read once; a `for` variable takes its list (`for p in a b` makes `$p` = `a b`), unknown when the list is files or command output; a `while`/`until` condition read as a command | `facts` |
| S4d | A function | Its body read where it is defined, its arguments unknown | `facts` |
| S4e | A `RUN` under `SHELL ["powershell", ...]` (or `pwsh`, `cmd`) | Counted apart, as another shell: neither read nor in the unreadable rate | `facts`, `report` |
| — | A pipe into a shell | Stays unreadable: the script it runs is downloaded or generated | — |

After S4a-S4e, docker-library is measured again and the rest classified.

## Questions

1. Loops: read the body once, with the loop variable taking the list (`for p in a b; do apk add $p; done` counts `a`
   and `b`)?
2. Functions: read a function's body as if it is called?
3. PowerShell: count those `RUN`s apart, as "another shell", not as unreadable?

Answers (6 Nov): 1, yes, the loop variable takes the list; 2, yes, read the body; 3, count them apart.

## Tests, in order

1. `{ a; b; } > f` and `( cd x && make )` read as their commands.
2. `case "$arch" in amd64) A=x;; arm64|aarch64) A=y;; *) exit 1;; esac` reads every branch's commands, not the
   patterns.
3. `for p in a b; do apk add $p; done` counts packages `a` and `b`; `for f in /etc/*.conf; do ...; done` leaves
   `$f` as written; `while read -r l; do ...; done` reads the condition and the body.
4. `install() { apt-get install -y curl; }` and `function install { ... }` read the body.
5. A `RUN` after `SHELL ["powershell", ...]` is counted as another shell, not read and not unreadable; rates and the
   report say how many.
6. docker-library's real lines for each construct read.

