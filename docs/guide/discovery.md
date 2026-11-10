# Discovery: finding the Dockerfiles

To discover and survey an organisation in one step, see [the survey](../../README.md#survey-an-organisation). To run
discovery alone:

```bash
export GHE_API_URL=https://github.example.com       # or https://github.com/MyOrg, or https://myco.ghe.com
export GHE_TOKEN=...                               # read access to every organisation (or GHE_TOKEN_FILE)
export OUT_DIR=/data/inventory                     # keep it between runs: a rerun resumes
export ORGANIZATIONS=payments,platform             # optional: only these (default: the URL's org, else all)
export RELEASE_INDEX=/data/releases.csv            # optional: owner/repo,commitSha per line
export CONCURRENCY=8                               # optional: parallel repository scans
export TRY_REPOSITORIES=10                         # optional: stop once this many repositories held a Dockerfile
docker-inventory discovery
```

Each repository is scanned at the commit the release index names. A repository with no entry is scanned at
its default branch head and flagged, so the report can show how much of the estate is not release-scoped.
Interrupted runs resume: finished repositories and fetched Dockerfiles are skipped, and failed repositories
are retried. GitHub's rate limits are respected: when GitHub asks to slow down, or the hourly allowance runs out,
discovery waits as long as GitHub says and carries on.

A file counts as a Dockerfile when it is named `Dockerfile` or `Containerfile`, or carries either as a prefix or
suffix (`Dockerfile.rhel9`, `app.Dockerfile`), in any directory. Not counted: files named like Dockerfiles that hold
source, documents, templates, images or grammars (`dockerfile.go`, `Dockerfile.md`, `Dockerfile.j2`,
`Containerfile.dockerignore`, `dockerfile.svg`, `dockerfile.bnf`),
symlinks, third-party copies (`vendor/`, `node_modules/`, `third_party/`) and test inputs (`testdata/`, `testfiles/`).

## What it writes

| File | Contents |
| --- | --- |
| `manifest.jsonl` | One line per Dockerfile found: repository, commit, scope (`RELEASED` or `DEFAULT_BRANCH`), path, blob SHA |
| `blobs/<sha>` | Each distinct Dockerfile's text, stored once however many repositories copy it |
| `repos.jsonl` | One line per repository per run: outcome (`SCANNED`, `EMPTY`, `TRUNCATED`, `RELEASE_COMMIT_MISSING`, `FAILED`), commit, archived and fork flags, Dockerfile count |
| `blob-findings.jsonl` | Dockerfiles and YAML that could not be stored: binary, too large, or not found |
| `yaml.jsonl`, `yaml/` | Every YAML file that could be a deployment manifest (not vendored, tests, docs, examples, CI or Helm's files), its locations and each file once by blob SHA, apart from the Dockerfiles; a discovery run before 9 Nov lists none, and resuming it does not add them: discover into a new folder |
| `changes.jsonl` | One line per location: the last commit that changed the Dockerfile up to the scanned commit, and its date, both empty where GitHub has no history for it; a location GitHub refused (an error, counted in the summary) is asked for again on the next run |

Several organisations are surveyed as one estate by giving the survey each one's URL: it discovers each into
`<out>/discovery-of/<organisation>/` and merges them into `<out>/discovery/` (every file, each line once, each
Dockerfile stored once). Discover each organisation only once, or its repositories are counted twice (the survey
says which).
