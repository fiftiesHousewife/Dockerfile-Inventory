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
| 1. Parse each Dockerfile into facts | `facts` | In progress: parser in place |
| 1b. Link Artifactory images to their Dockerfiles; gap list | `lineage` | Not started |
| 2. Cluster into archetypes; estate report | `report` | Not started |
| 3. Quality rules, then fixes through Renovate and OpenRewrite | `rules` | Not started |
| 4. Dockerfile builders and one shared renderer | `dsl` | Not started |
| 5. Convert existing Dockerfiles to builders | `converter` | Not started |

## Requirements

- JDK 25, the newest long-term-support release. Gradle finds an installed JDK 25; it does not download one.
- For discovery: a GitHub Enterprise token that can read every organisation's repositories, and optionally the
  release index exported from the release database.

## Build

```bash
./gradlew check
```

## Run discovery

```bash
export GHE_API_URL=https://github.example.com/api   # the server's /api URL
export GHE_TOKEN=...                               # read access to every organisation
export OUT_DIR=/data/inventory                     # keep it between runs: a rerun resumes
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

## Contributing

Notes for contributors and coding agents, including how work is planned and test-driven, are in
[AGENTS.md](AGENTS.md). Per-phase plans are in [docs/plans/](docs/plans/).
