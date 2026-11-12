# Discovery: finding the Dockerfiles

To discover and survey an organisation in one step, see [the survey](../../README.md#survey-an-organisation). To run
discovery alone:

```bash
export GHE_API_URL=https://github.example.com       # or https://github.com/MyOrg, or https://myco.ghe.com
export GHE_TOKEN=...                               # read access to every organisation (or GHE_TOKEN_FILE)
export OUT_DIR=/data/inventory                     # keep it between runs: a rerun resumes
export ORGANIZATIONS=payments,platform             # optional: only these (default: the URL's org, else all)
export RELEASE_INDEX=/data/releases.csv            # optional: owner/repo,commitSha per line
export GHE_MAX_REQUESTS_PER_SECOND=2              # the most calls to GitHub a second (or GHE_LOAD=gentle)
export GHE_CONCURRENCY=8                           # optional: parallel repository scans
export TRY_REPOSITORIES=10                         # optional: stop once this many repositories held a Dockerfile
export GHE_LAST_CHANGES=true                       # optional, over REST only: read each location's last change
export GHE_API=rest                                # optional: REST alone, whatever the credential (or --api rest)
docker-inventory discovery
```

### A token GraphQL refuses

Discovery reads blobs and last changes 50 to a GraphQL query. Some tokens may not use GraphQL (a fine-grained
personal access token on a server that does not take them there). Discovery asks each credential once, at the start,
with the smallest GraphQL query; one that is refused (401, 403, or an answer with errors) reads its organisations
over REST alone, and the log says so:

```
discovery: a token may not use GraphQL, so its organisations are read over REST, a call a blob, without last changes (GHE_LAST_CHANGES=true reads them)
```

The output is the same, but REST takes a call for each blob and each location where GraphQL takes one for fifty, and
one more for each repository the release index does not name (its default branch's head). The confirm step's
estimate counts the calls each way. Last changes are left out over REST unless `GHE_LAST_CHANGES=true`, since they
are a call a location; without them the Dockerfile pages show no "last changed" date. A run that mixes an App and a
token reads each organisation the fastest way its credential allows.

`--api rest` (`GHE_API=rest`, `github.api: rest`) reads every organisation over REST without asking, to try the REST
path with a token GraphQL accepts; the default, `auto`, asks. To check that both give the same output, run
`GH_TOKEN=$(gh auth token) ./gradlew :discovery:githubLive`: it discovers one public repository on github.com
(`LIVE_REPOSITORY`, by default `sclorg/postgresql-container`) over GraphQL and over REST, with last changes, and fails
naming any file that differs.

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
