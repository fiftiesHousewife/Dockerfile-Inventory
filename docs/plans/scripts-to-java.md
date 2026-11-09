# The scripts as Java: plan

Status: planned 9 Nov; the questions answered 9 Nov; slice 1 built 9 Nov.

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

