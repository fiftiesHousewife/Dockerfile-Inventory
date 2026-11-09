# The scripts as Java: plan

Status: planned 9 Nov; the questions answered 9 Nov; slice 1 built 9 Nov; slice 2 built 9 Nov; slice 3 (the container
demo) next.

## Why

Asked 8 Nov: the remaining shell scripts replaced by Java (mains, Gradle tasks, tagged tests), the container's entry
point (`scripts/inventory`) aside. Scripts are untested, untyped, and run differently on macOS and Linux; the rest of
the pipeline is Java with tests.

## What there is (9 Nov)

| Script | Lines | Does | For |
| --- | --- | --- | --- |
| `discover.sh` | 14 | discovery on one organisation with the GitHub CLI's login | people; the survey covers it now |
| `combine.sh` | 30 | several discovery outputs merged into one estate | people |
| `corpus-collect.sh` | 105 | Dockerfiles that broke something, from a run, into the fixture bank | contributors |
| `corpus-collect-images.sh` | 42 | real images and their candidates, from a lineage run, into the fixture bank | contributors |
| `record-jcr-fixtures.sh` | 62 | a local JCR's replies to lineage's requests, as test fixtures | contributors |
| `okd-select.sh` | 112 | OKD release images chosen for a lineage run, from quay.io | contributors |
| `lineage-demo.sh` | 293 | PostgreSQL and JCR in containers, Buildah builds or mirrored images, lineage against them | contributors |
| `jcr-diagnose.sh` | 59 | why the local JCR does not answer | contributors |

## Design

- The survey takes several organisations (asked 9 Nov): `survey [--try] <URL>...`. With one, nothing changes. With
  several, each is discovered into `<out>/discovery-of/<organisation>/` and merged into `<out>/discovery/`, which the
  later stages read as before; `<out>` is `out/` and the organisations' folder names joined by `+` (`OUT_DIR`
  overrides). A try covers ten repositories with a Dockerfile in each. `discover.sh` and `combine.sh` go.
- The merge is `Combine` in `discovery` (it knows discovery's files): every JSON Lines file a discovery output holds
  (`manifest.jsonl`, `repos.jsonl`, `changes.jsonl`, `blob-findings.jsonl`), each line once, and each blob once by its
  SHA (never a `.part`); a folder that is no discovery output is refused by name; a repository in more than one
  source is reported. `combine.sh` merged only the first two, so a source discovered since `changes.jsonl` came
  would have lost its last changes in the merge.
- The contributors' tools in one `tools` module, left out of the image (asked 9 Nov): a main per tool, each with a
  Gradle task and tests on small fixtures: the two corpus collectors, the JCR fixture recorder, the OKD image picker.
- The container demo ported to `tools` too (asked 9 Nov): `lineage-demo.sh` and `jcr-diagnose.sh`, on the container
  handling the converter's image comparison has; its steps tested, the run end to end a tagged test that skips
  without Docker.
- Afterwards `scripts/` holds only `inventory`, the image's entry point.

## Slices

1. `Combine`; the survey over several organisations; `discover.sh` and `combine.sh` deleted.
2. The `tools` module: the corpus collectors, the JCR fixture recorder, the OKD image picker.
3. The container demo.

## Tests, slice 1

1. `Combine.of`: every JSON Lines file merged, each line once; blobs once, never a `.part`; a folder that is no
   discovery output refused by name; the repositories in more than one source.
2. `Survey.steps` with several URLs: a discovery per organisation, the merge, then the stages as before; the folder.
3. `SurveyMain.arguments` with several URLs.
4. Measured: the stored sources of `out/all` merged again, against what `combine.sh` wrote.

## Questions answered

1. `discover.sh` and `combine.sh`: the survey takes several organisations and merges them (9 Nov).
2. The contributors' tools: one `tools` module (9 Nov).
3. The container demo: ported to `tools` (9 Nov).

## Built, slice 1 (9 Nov)

Tests 1 to 3 as listed, each seen to fail first. Measured: the six sources `out/all` was made from (demo4, okd,
konflux-ci, redhat-developer, docker-library, bitnami) merged again by `Combine`: 8,199 locations, 1,453
repositories and 5,250 Dockerfiles, line for line and file for file what `combine.sh` wrote. Those sources predate
`changes.jsonl`, so nothing had been lost there. `discover.sh` and `combine.sh` deleted; the README surveys several
organisations instead, and runs discovery alone through Gradle with the CLI's login.

## Slice 2: the Dockerfile collector (9 Nov)

`CorpusCollect` in the new `tools` module (`./gradlew collectCorpus --args=<run>`, settings as the script's):
`RunOutcomes` reads the run's five files, `Workaround` tells a retried file's workaround from its text, `CorpusCase`
writes `expected.json` as jq printed the bank's earlier cases. Its tests run on a small run holding one of each kind,
a secret and an existing case. Measured on `out/all` against the script, each into an empty folder: the same 129
cases, byte for byte (the script's `sort -u` keeps each blob's first location in the manifest, which the collector
takes too). `corpus-collect.sh` deleted.

`ImageCollect` (`./gradlew collectImages --args=<run>`): each placed image's trimmed config, the facts of every
Dockerfile in the repository its source label names, and its link, now with `credited`, which the corpus test reads
since 9 Nov. `Json` writes the bank's JSON as jq laid it out (every array element on its own line, `[]` and `{}`
when empty), tested byte for byte on a committed case. Measured on OKD's lineage against the script: the same 34
cases (an image pushed under two names takes the first the run recorded, as the script did), every `config.json` and
`dockerfiles.jsonl` byte for byte, `expected.json` the same but for `credited`. `corpus-collect-images.sh` deleted.

`OkdSelect` (`./gradlew selectOkd --args="[release] [output directory]"`, settings as the script's): `OkdChoice`
holds the choosing (tools first, one image per scanned repository spread evenly, within the count with every base),
`Quay` the reading of quay.io, `QuayHttp` it over HTTP through the pipeline's `http` settings, tested against a
loopback server. Measured offline: the stored OKD tag list and configs given as quay.io, all seven files
(`tags.txt` to `releases.csv`) byte for byte what the script wrote. Not yet tried live: this session's network does
not reach quay.io; the owner's run is the check. `okd-select.sh` deleted.

`JcrRecord` (`./gradlew recordJcrFixtures`, settings as the script's): the same requests in the same order, each
body kept byte for byte and indexed with its status, Content-Type and Docker-Content-Digest. Its test replays the
committed fixtures through a loopback server playing JCR (every reply `index.tsv` lists, the ping and the token) and
expects them back: all 22 replies and `index.tsv`, byte for byte. Not tried against a live JCR (that waits for slice
3's demo). `record-jcr-fixtures.sh` deleted.

