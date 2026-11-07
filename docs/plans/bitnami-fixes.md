# bitnami's estate: plan

Status: planned 6 Nov; questions answered 7 Nov; built 7 Nov.

`bitnami` (22 repositories, 266 Dockerfile locations, 266 files, 257 of them in `bitnami/containers`, all on the
default branch) parses completely and every file passes the round trip. Two gaps, each in nearly every file:

| # | Problem | Files | Effect |
| --- | --- | --- | --- |
| B1 | A bash array, `COMPONENTS=( "a" "b" ) ; for C in "${COMPONENTS[@]}"`, in the shared download `RUN` | 257 | That `RUN` is unreadable: 257 of 1,656 RUNs (15.5%), the highest rate of any estate since S4 |
| B2 | `install_packages a b` and `uninstall_packages a`, minideb's wrappers around `apt-get install -y --no-install-recommends` (cleaning the lists after) and `apt-get remove` | 260 and 201 | Their packages are not counted, so bitnami's inventory misses most installs |

Found once B1 and B2 were built, in every estate:

| # | Problem | Findings | Effect |
| --- | --- | --- | --- |
| B3 | R1 reports `FROM scratch` as not pinned by digest; `scratch` is Docker's empty base, not an image, and has no digest | bitnami 42, openshift 196, konflux-ci 526, redhat-developer 34, docker-library 2 | False positives |
| B4 | S2 reports a secret-named `ENV` whose value is a path to a file (`NSS_WRAPPER_PASSWD="/opt/bitnami/spark/tmp/nss_passwd"`, `DOCKER_CREDENTIAL_GCR_CONFIG="/kaniko/.config/..."`) | bitnami 3 | Says where a secret lives, not what it is |

## Design

| # | Fix | Module |
| --- | --- | --- |
| B1 | `ShellWords` reads `NAME=( words )` as one assignment word; `ShellCommands` stores the array's words, joined by spaces, as the variable's value, so `"${NAME[@]}"` and `${NAME[*]}` expand to them in a `for` list or a command, like any other variable (an element by index stays as written) | `facts` |
| B3 | R1 skips a final base of `scratch` | `rules` |
| B4 | `Secrets.secret(name, value)`: a secret shape, or a secret name whose value is not only an absolute path (starts with `/`, no whitespace); used by S2 and the archetype cards, which then show such a path (the owner's choice, 7 Nov) | `dsl`, `rules`, `report` |
| B2 | `PackageCalls` reads `install_packages a b` as `apt-get install a b` with its cache cleaned, and `uninstall_packages a` as `apt-get remove a` (the owner's choice, 7 Nov) | `facts` |

## Questions

1. B2: count `install_packages` as an apt install (manager `apt-get`, subcommand `install`, cache already
   cleaned), or as its own manager `install_packages`, or not at all?
2. B1: read a bash array as a variable holding its words, as above?
3. B4: should a secret-named value that is only an absolute path (no spaces) be left out of S2?

Answers (7 Nov): 1, as `apt-get`; 2, yes, an array holds its words; 3, yes, skip absolute paths.

## Tests, in order

1. `A=( x "y z" ) ; for c in "${A[@]}"; do echo $c; done` reads, and `c` takes `x y z`.
2. bitnami's real download `RUN` reads.
3. `install_packages ca-certificates curl` is an `apt-get install` of both, cleaned; `uninstall_packages curl` an
   `apt-get remove`, installing nothing.
4. bitnami measured again: unreadable RUNs, package calls.
5. R1 leaves `FROM scratch` alone; S2 and the archetype cards leave a secret-named absolute path alone.

## As built

`ShellWords` reads `NAME=( words )` as one assignment word and `Expansions` expands `${NAME[@]}` and `${NAME[*]}`;
`PackageCalls` maps a base image's wrapper to its manager (`install_packages` -> `apt-get install`, cleaned;
`uninstall_packages` -> `apt-get remove`); the converter does not, so such a `RUN` stays shell text. R1 skips
`scratch`; `Secrets.secret(name, value)` is what S2 and the cards ask.

| | Before | After |
| --- | --- | --- |
| bitnami unreadable RUNs | 257 of 1,656 (15.5%) | 0 |
| bitnami package calls | 648 | 1,110 |
| openshift unreadable RUNs | 44 | 43 (one array) |
| konflux-ci, redhat-developer, docker-library unreadable RUNs | 8, 12, 18 | unchanged |
| R1 findings: bitnami, openshift, konflux-ci, redhat-developer, docker-library | 264, 2,419, 640, 438, 574 | 222, 2,245, 116, 404, 572 |
| S2 findings: bitnami, openshift, redhat-developer | 3, 11, 2 | 0, 7, 1 |

bitnami's round trip stays 266 of 266.

Follow-up (7 Nov): the dashboard's "Bases pinned by digest" counted `scratch` as an unpinned base too; `Summary` now
leaves it out of that share (the base table keeps its row). bitnami 0.8% -> 0.9% (2 of 224: its bases really are
unpinned); konflux-ci, with 526 `scratch` locations, the largest change.

