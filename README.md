# Dockerfile Inventory

Finds every Dockerfile across a GitHub Enterprise estate at the commit that was last released, parses each
unique one, and reports what the estate is built from: base images, package managers, users, and how many
distinct kinds of Dockerfile there really are. Later stages link the images in Artifactory back to the
Dockerfiles that built them and check both against house rules.

The full plan, with each phase's gate, is the
[delivery plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R); progress is in [BACKLOG.md](BACKLOG.md).

## Status

| Stage | Module | State |
| --- | --- | --- |
| 0. Discovery: find Dockerfiles, fetch each unique one once | `discovery` | Built; waiting on a GHE token for the pilot run |
| 1. Parse each Dockerfile into facts | `facts` | Built; its rates need a discovery run |
| 1b. Link Artifactory images to their Dockerfiles; gap list | `lineage` | Built; waiting on an Artifactory token for a real run |
| 2. Cluster into archetypes; estate report | `report` | Built; its numbers need real discovery and facts runs |
| 3. Quality rules, then fixes through Renovate and OpenRewrite | `rules` | Detection built; fixes wait on reviewed samples |
| 4. Dockerfile builders and one shared renderer | `dsl` | Library built; the central generator repository waits on a GHE write token |
| 5. Convert existing Dockerfiles to builders | `converter` | Built; building and comparing images (5b) needs Buildah |

## Requirements

- JDK 25, the newest long-term-support release. Gradle downloads it if it is not installed.
- Network access for `./gradlew check`: it fails when any dependency, plugin, Gradle or the JDK is behind its
  newest stable release. Offline, run `./gradlew check -x verifyUpToDate`.
- For discovery: a GitHub Enterprise token that can read every organisation's repositories, and optionally the
  release index exported from the release database.

## Build

```bash
./gradlew check
```

## Run discovery

```bash
export GHE_API_URL=https://github.example.com/api   # the server's /api URL, or https://api.github.com
export GHE_TOKEN=...                               # read access to every organisation
export OUT_DIR=/data/inventory                     # keep it between runs: a rerun resumes
export ORGANIZATIONS=payments,platform             # optional: only these organisations (default: all)
export RELEASE_INDEX=/data/releases.csv            # optional: owner/repo,commitSha per line
export CONCURRENCY=16                              # optional: parallel repository scans
./gradlew :discovery:run
```

Each repository is scanned at the commit the release index names. A repository with no entry is scanned at
its default branch head and flagged, so the report can show how much of the estate is not release-scoped.
Interrupted runs resume: finished repositories and fetched Dockerfiles are skipped, and failed repositories
are retried.

### What it writes

| File | Contents |
| --- | --- |
| `manifest.jsonl` | One line per Dockerfile found: repository, commit, scope (`RELEASED` or `DEFAULT_BRANCH`), path, blob SHA |
| `blobs/<sha>` | Each distinct Dockerfile's text, stored once however many repositories copy it |
| `repos.jsonl` | One line per repository per run: outcome (`SCANNED`, `EMPTY`, `TRUNCATED`, `RELEASE_COMMIT_MISSING`, `FAILED`), commit, archived and fork flags, Dockerfile count |
| `blob-findings.jsonl` | Dockerfiles that could not be stored: binary, too large, or not found |

## Parse the Dockerfiles

```bash
export DISCOVERY_DIR=/data/inventory     # discovery's OUT_DIR
export OUT_DIR=/data/facts
./gradlew :facts:run
```

Each distinct Dockerfile is parsed once; a rerun parses only new ones.

| File | Contents |
| --- | --- |
| `facts.jsonl` | One line per Dockerfile: base image, package-manager calls per stage, final user and whether it is root, numeric or named, exposed ports, entrypoint form, BuildKit features, `RUN` counts, two hashes for grouping (cosmetic and functional duplicates), and the final image's instructions and base image path, which lineage matches built images against |
| `parse-failures.jsonl` | Dockerfiles the parser rejected, with the reason |
| `rates.json` | Parse-failure rate, the share of `RUN`s too complex to read, unresolved `FROM` per organisation, BuildKit feature use |

## Link images to Dockerfiles

```bash
export ARTIFACTORY_URL=https://artifactory.example.com/artifactory/
export ARTIFACTORY_TOKEN=...                  # read access to every local Docker repository; REST only, no AQL
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export OUT_DIR=/data/lineage                  # keep it between runs: a rerun resumes the Artifactory reading
export CONCURRENCY=16                         # optional: images read at once
./gradlew :lineage:run
```

Every tag of every image in every local Docker repository is read once (`linux/amd64` only); each image config is
fetched once however many tags share it. Images are then placed in a family tree by their layers and linked to
the Dockerfiles whose instructions match their build history.

| File | Contents |
| --- | --- |
| `images.jsonl`, `configs/` | Every tag, with the image config it names or why it was skipped; each config once |
| `lineage.jsonl` | Each image's parent: another image, none (a root), or several equally likely |
| `links.jsonl` | Each image's Dockerfiles: `STRONG` (exact, and at a released commit), `PROBABLE` (exact), `WEAK` (close, on the same base), or `NONE`; and whether a root's Dockerfile names a base Artifactory holds |
| `effective.jsonl` | Each image's user, environment, labels, working directory, entrypoint and command, and which image in its chain set each one |
| `gaps.jsonl` | Images built here that no scanned Dockerfile matches |
| `external-bases.jsonl` | Unmatched roots: external bases mirrored into Artifactory |
| `outdated-bases.jsonl` | Images built on a base that has since been pushed again under the same name |
| `rates.json` | Tags, images, skips, lineage coverage, links by tier, gaps |

## Report on the estate

```bash
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export LINEAGE_DIR=/data/lineage              # optional: effective users from the lineage stage
export EOL_LIST=/data/eol.csv                 # optional: imagePath,tagPrefix,status,eolDate
export OUT_DIR=/data/report
./gradlew :report:run
```

Every Dockerfile location counts once. Dockerfiles are collapsed in four passes, cheapest first: identical files,
files identical once comments and whitespace are normalised, files with the same signature (base family and major
version, package managers and packages, user kind, ports, entrypoint form, stage count), and near-duplicates
(package set and base family at least 80% similar). What is left are the archetypes.

| File | Contents |
| --- | --- |
| `passes.json` | Groups and locations after each pass; k80 and k95, the fewest archetypes covering 80% and 95% of locations |
| `archetypes.json` | Every archetype by size: its files, signature, a representative file and the repositories using it |
| `tables/*.csv`, `tables/ddl.sql` | `dockerfiles`, `locations`, `archetypes` and `bases` for loading into Oracle, with the `CREATE TABLE` statements |
| `dashboard.html` | One self-contained page: the counts, rates, top archetypes and bases, package managers and users |

## Check the estate against the rules

```bash
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR: blobs and manifest
export LINEAGE_DIR=/data/lineage              # optional: rules about inherited users and bases
export ALLOW_LIST=/data/approved-bases.csv    # optional: imagePath,tagPrefix (rule S1)
export EOL_LIST=/data/eol.csv                 # optional: imagePath,tagPrefix,status,eolDate (rule R2)
export OUT_DIR=/data/rules
./gradlew :rules:run
```

Detect-only: nothing is changed. Each distinct Dockerfile is checked once; a rule whose input is missing is
skipped and listed in `rates.json`. Secret-shaped values are never written out, only a SHA-256 of the value and
its line.

| Rule | Finds | Severity |
| --- | --- | --- |
| S1 | A base, or the base at the root of an image's chain, not on the approved list | High |
| S2 | A secret-shaped `ENV` or `ARG` value | Critical |
| S3 | `curl` or `wget` piped to a shell | High |
| S4 | An image that cannot run as an arbitrary non-root UID (OpenShift's default) | High |
| R1 | A base not pinned by digest | Medium |
| R2 | An end-of-life base | Medium |
| R3 | No OCI revision or source label | Low |
| H1 | An RPM install with no clean in the same `RUN` | Low |
| H2 | A shell-form entrypoint | Low |
| H3 | Adjacent `RUN`s that could be one | Low |
| I1 | Packages installed without a version | Info |
| I2 | A Dockerfile the parser cannot read | Medium |

| File | Contents |
| --- | --- |
| `findings.jsonl` | One finding per rule per distinct Dockerfile, with its line where known; rules about inherited properties name the Dockerfile to change |
| `tables/findings.csv`, `tables/ddl.sql` | One row per finding per location, for Oracle |
| `rates.json` | Findings, files and locations per rule; the rules skipped |
| `samples/<rule>.csv` | Up to 50 findings per rule, spread across organisations, with a `verdict` column for people to classify before any fix is enabled |

`renovate/dockerfile-estate.json` is a Renovate preset for the central instance: Dockerfile bases pinned by
digest, base updates grouped into one pull request per repository.

## Convert Dockerfiles to builder classes

```bash
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export OPT_IN=/data/opt-in.txt                # optional: owner/repo per line; only these get classes
export ALLOW_LIST=/data/approved-bases.csv    # optional: approved bases, for the TODO list
export OUT_DIR=/data/converter
./gradlew :converter:run
```

Every distinct Dockerfile is converted to the builder model faithfully, so the generated class reproduces the
image as it is, and is checked by a round trip: the rendered conversion must parse back to the same model and
have the same instructions as the original. Policy breaches become `// TODO(<rule>)` comments, for later fixes.

| File | Contents |
| --- | --- |
| `generated/images/<owner>/<Name>Image.java` | One builder class per opted-in Dockerfile that passed the round trip |
| `roundtrip.jsonl` | Each Dockerfile, whether it passed, and the first difference when not |
| `coverage.json` | Typed instructions over all instructions per Dockerfile, and how often each reason left an instruction untyped |

## Contributing

Notes for contributors and coding agents, including how work is planned and test-driven, are in
[AGENTS.md](AGENTS.md). Per-phase plans are in [docs/plans/](docs/plans/).
