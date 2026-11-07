# Dockerfile Inventory

Finds every Dockerfile across a GitHub Enterprise estate at the commit that was last released, parses each
unique one, and reports what the estate is built from: base images, package managers, users, and how many
distinct kinds of Dockerfile there really are. Later stages link the images in Artifactory back to the
Dockerfiles that built them and check both against house rules.

Why it is built the way it is: [the overview](docs/overview.md). The full plan, with each phase's gate, is the
[delivery plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R); progress is in [BACKLOG.md](BACKLOG.md).

## Status

| Stage | Module | State |
| --- | --- | --- |
| 0. Discovery: find Dockerfiles, fetch each unique one once | `discovery` | Built; run on six public organisations (`sclorg`, `openshift`, `konflux-ci`, `redhat-developer`, `docker-library`, `bitnami`); the enterprise pilot waits on a GHE token |
| 1. Parse each Dockerfile into facts | `facts` | Built; on the public runs parse failures 0-2.2% and unreadable `RUN`s 0-1.2% |
| 1b. Link Artifactory images to their Dockerfiles; gap list | `lineage` | Built; tried end to end against a local JFrog Container Registry, on images built with Buildah and on 30 published OKD images (every parent right, all 26 components linked); a real run waits on an Artifactory token |
| 2. Cluster into archetypes; estate report | `report` | Built; its numbers need the enterprise discovery and facts runs |
| 3. Quality rules, then fixes through Renovate and OpenRewrite | `rules` | Detection built; fixes wait on reviewed samples |
| 4. Dockerfile builders and one shared renderer | `dsl` | Library built; the central generator repository waits on a GHE write token |
| 5. Convert existing Dockerfiles to builders | `converter` | Built; building and comparing images (5b) needs Buildah |

The Dockerfile parser the pipeline uses is also a library of its own: see [parser/README.md](parser/README.md).

## Example reports

Dashboards from runs on public GitHub organisations, as the report stage writes them (private links; ask the owner
for access):

- [all six organisations combined](https://claude.ai/artifact/Npbva376tdsXBuHsaTko2B): 8,199 Dockerfile locations in
  1,453 repositories (sclorg, openshift, konflux-ci, redhat-developer, docker-library, bitnami), with OKD lineage
- [openshift with lineage](https://claude.ai/artifact/6Uy3wKm1Wq9p2gwTdeGBPW): 2,919 Dockerfiles, with 30 of OKD's
  published images linked to the Dockerfiles that built them
- [bitnami](https://claude.ai/artifact/1wnH7fkxBzUTo2M1eA6SBH): 266 Dockerfiles on one pattern

## Requirements

- JDK 25, the newest long-term-support release. Gradle downloads it if it is not installed.
- Network access for `./gradlew check`: it fails when any dependency, plugin, Gradle or the JDK is behind its
  newest stable release. Offline, run `./gradlew check -x verifyUpToDate`.
- Behind a firewall that blocks Maven Central and the Gradle plugin portal: set `INVENTORY_MIRROR` to one Maven
  repository that serves both (an Artifactory remote or virtual repository), with `INVENTORY_MIRROR_USER` and
  `INVENTORY_MIRROR_PASSWORD` if it needs them. Where the JDK cannot be downloaded, install JDK 25 and add
  `org.gradle.java.installations.auto-download=false` to `~/.gradle/gradle.properties`; the up-to-date check also asks
  services.gradle.org and Adoptium, so skip it there (`-x verifyUpToDate`).
- For discovery: a GitHub Enterprise token that can read every organisation's repositories, and optionally the
  release index exported from the release database.

## Build

```bash
./gradlew check
```

## Inside a corporate network

Discovery and lineage read these from the environment as well; every one has a default.

| Setting | Default | What it does |
| --- | --- | --- |
| `HTTPS_PROXY`, `HTTP_PROXY` (either case) | none: direct | The outbound proxy, as `http://proxy:8080`; a user and password in the URL (`http://user:pass@proxy:8080`) answer the proxy's challenge and are never printed |
| `NO_PROXY` | none | Hosts that go direct: names, `.domain` suffixes, addresses, or `*` |
| `TRUST_BUNDLE` | the JDK's trust store | A PEM file of extra CA certificates (a corporate CA, a TLS-inspecting proxy's), trusted beside the JDK's own; on OpenShift, the injected `tls-ca-bundle.pem` |
| `GHE_TOKEN_FILE`, `ARTIFACTORY_TOKEN_FILE` | none | Read the token from a file instead of the variable (set one or the other); the file is read again whenever it changes and after a 401, so a token rotated by a mounted secret or a Vault agent is picked up mid-run |
| `CONNECT_TIMEOUT`, `HTTP_TIMEOUT` | `PT10S`, `PT60S` | ISO-8601 durations |
| `HTTP_RETRIES` | `5` | Attempts for a call that fails on the network or with a 502, 503 or 504, with growing waits (1 s, 2 s, 4 s ... up to 60 s) |
| `MAX_RATE_LIMIT_WAIT` | `PT1H` | The longest wait for a rate limit to reset; a longer one stops the run with the reset time, and a rerun resumes. With under a tenth of GitHub's limit left, discovery also slows its calls to last until the reset |

Each stage prints the settings it uses, without secrets, before it starts. A bad value stops it, naming the
variable. A GitHub organisation that enforces SAML single sign-on stops discovery at once with the link to authorise
the token; a repository or image Artifactory refuses is recorded as refused (`REFUSED` in lineage's rates) and the
rest is read.

## Run as a container

The `Containerfile` builds one image holding every stage, for a job in a pod (UBI 9 minimal, a trimmed Java 25
runtime, a non-root user, a home folder an arbitrary OpenShift user ID can write to, the heap sized from the pod's
memory limit):

```bash
buildah build -t dockerfile-inventory -f Containerfile .        # behind a firewall: --build-arg INVENTORY_MIRROR=...
podman run --rm -e GHE_API_URL -e GHE_TOKEN_FILE=/secrets/ghe-token -v ./out:/work/out:Z \
    -v ./secrets:/secrets:ro,Z -e OUT_DIR=/work/out/discovery dockerfile-inventory discovery
```

`inventory <stage>` runs `discovery`, `facts`, `lineage`, `report`, `rules` or `converter`, with the settings below
from the environment; `JAVA_OPTS` (default `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`) is passed to Java.
Mount the token as a file and set `GHE_TOKEN_FILE` or `ARTIFACTORY_TOKEN_FILE`, so a rotated secret is picked up.

## Run discovery

To try one public or enterprise organisation with the GitHub CLI's login (`gh auth login`), so no token is typed:

```bash
scripts/discover.sh https://github.com/openshift out/openshift/discovery
```

In full:

```bash
export GHE_API_URL=https://github.example.com       # or https://github.com/MyOrg, or https://myco.ghe.com
export GHE_TOKEN=...                               # read access to every organisation (or GHE_TOKEN_FILE)
export OUT_DIR=/data/inventory                     # keep it between runs: a rerun resumes
export ORGANIZATIONS=payments,platform             # optional: only these (default: the URL's org, else all)
export RELEASE_INDEX=/data/releases.csv            # optional: owner/repo,commitSha per line
export CONCURRENCY=8                               # optional: parallel repository scans
./gradlew :discovery:run
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

### What it writes

| File | Contents |
| --- | --- |
| `manifest.jsonl` | One line per Dockerfile found: repository, commit, scope (`RELEASED` or `DEFAULT_BRANCH`), path, blob SHA |
| `blobs/<sha>` | Each distinct Dockerfile's text, stored once however many repositories copy it |
| `repos.jsonl` | One line per repository per run: outcome (`SCANNED`, `EMPTY`, `TRUNCATED`, `RELEASE_COMMIT_MISSING`, `FAILED`), commit, archived and fork flags, Dockerfile count |
| `blob-findings.jsonl` | Dockerfiles that could not be stored: binary, too large, or not found |

Several discovery runs (one per organisation, say) are merged into one estate for the stages after it:

```bash
scripts/combine.sh out/all/discovery out/openshift/discovery out/bitnami/discovery   # any number of sources
```

Locations and repositories are concatenated and each file is stored once; discover each organisation only once, or
its repositories are counted twice (the script warns).

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
| `parse-failures.jsonl` | Dockerfiles the parser rejected, with the reason; a template saved under a Dockerfile's name (Jinja and similar) says so |
| `rates.json` | Parse-failure rate (templates left out and counted apart), the share of `RUN`s too complex to read, `RUN`s in another shell, unresolved `FROM` per organisation, BuildKit feature use |

The shell in each `RUN` is read for its commands: command substitutions (`$(uname -m)`), groups and subshells,
`if` and `case` (every branch counts), loops (read once; `for p in a b` gives `$p` the list) and functions (their
bodies). A `RUN` is too complex to read only when it pipes a download into a shell or uses rarer forms; its packages
are then not counted. A `RUN` under `SHELL ["powershell", ...]` (or `pwsh`, `cmd`) is counted apart as another
shell. A known set of bugs in the Dockerfile parser is worked around by a second, adjusted parse that keeps
every line number; the rest are listed in `docs/upstream/rewrite-docker-issues.md`.

## Link images to Dockerfiles

```bash
export ARTIFACTORY_URL=https://artifactory.example.com/artifactory/
export ARTIFACTORY_TOKEN=...                  # read access to every local Docker repository (or ARTIFACTORY_TOKEN_FILE)
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export OUT_DIR=/data/lineage                  # keep it between runs: a rerun resumes the Artifactory reading
export CONCURRENCY=16                         # optional: images read at once
./gradlew :lineage:run
```

Every tag of every image in every local Docker repository is read once (`linux/amd64` only); each image config is
fetched once however many tags share it. Images are then placed in a family tree by their layers and linked to
the Dockerfiles whose instructions match their build history; an image whose source label
(`io.openshift.build.source-location` or `org.opencontainers.image.source`) names a repository is linked only to that
repository's Dockerfiles. Without `ARTIFACTORY_URL` only the linking runs again, on what `OUT_DIR` already holds.

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

### Trying lineage without an Artifactory

`scripts/lineage-demo.sh` starts PostgreSQL and a local JFrog Container Registry (JCR 7.77) in Docker, builds images
with Buildah from the CentOS Stream 9 Dockerfiles that discovery found (each at its scanned commit, each `FROM`
pointed at the image built before it), pushes them, and runs lineage, the report and the rules against them. It
needs Docker with 4 GB of memory or more, git, jq and curl, and a discovery and facts run to start from; the header
of the script lists its settings. By default it builds one chain (CentOS Stream 9, `s2i-core`, `s2i-base`, Python
3.12, nginx 1.24); `SCOPE=all` builds every `Dockerfile.c9s`, which takes hours when amd64 is emulated.

On a first run JCR's Docker API stays off until JFrog's JCR licence agreement is accepted: read it, then run with
`ACCEPT_JCR_EULA=yes` to accept it through the API. If another program holds port 8082, set `JCR_PORT`.
`scripts/jcr-diagnose.sh` shows why JCR is not answering: memory, its logs, which ports answer, and what its Docker
API replies.

To try lineage on images someone else built, OKD's published images can be copied in unchanged instead:

```bash
scripts/okd-select.sh                       # one OKD release on quay.io -> out/okd/mirror.txt, releases.csv
RELEASE_INDEX=out/okd/releases.csv scripts/discover.sh https://github.com/openshift out/okd/discovery
DISCOVERY_DIR=out/okd/discovery OUT_DIR=out/okd/facts ./gradlew :facts:run
MIRROR=out/okd/mirror.txt DISCOVERY_DIR=out/okd/discovery FACTS_DIR=out/okd/facts OUT=out/okd \
    scripts/lineage-demo.sh
```

`okd-select.sh` picks about 30 images (the bases, `tools` and `network-tools`, and components whose repository
discovery found, one per repository) and writes each chosen component's repository and the commit its source label
names, so discovery reads the Dockerfiles that built them. The mirror copies with skopeo, keeping digests.

## Report on the estate

```bash
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export LINEAGE_DIR=/data/lineage              # optional: effective users and the lineage section
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
| `dashboard.html` | One self-contained page (no external files), described below |

The dashboard opens with a health strip: six estate-wide shares, each marked good, watch or act in words as well as
colour (parse failures good at 1% or less, act above 5%; final user root 10% / 30%; bases pinned by digest 80% /
20%; bases at end of life 0% / 5%; scanned at a released commit 90% / 50%; `RUN`s too complex to read 5% / 15%).
Then: how many really different Dockerfiles there are, counted four ways; the archetype coverage curve with the 80%
and 95% points; the ten largest archetypes as cards, each with a sentence saying what it builds, its size, and one
example Dockerfile with secret values hidden; the top bases as bars coloured by end-of-life status (without
`EOL_LIST` every status is unknown, and the page says so); stacked bars for parse outcome, package managers and final
user; and, with `LINEAGE_DIR`, the lineage section: the family tree for a run under 50 images, otherwise the images
with no Dockerfile, those on an outdated base and the most-used bases. Every number and section has an info marker
explaining it; the tables behind each chart are folded underneath it.

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
| S2 | A secret-shaped `ENV` or `ARG` value (a value that is only an absolute path is not one) | Critical |
| S3 | `curl` or `wget` piped to a shell | High |
| S4 | An image that cannot run as an arbitrary non-root UID (OpenShift's default) | High |
| R1 | A base not pinned by digest (`scratch` has none) | Medium |
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
have the same instructions as the original. A Dockerfile that already uses heredocs or `RUN --mount` is rendered
for a builder that has them. Policy breaches become `// TODO(<rule>)` comments, for later fixes.

| File | Contents |
| --- | --- |
| `generated/images/<owner>/<Name>Image.java` | One builder class per opted-in Dockerfile that passed the round trip |
| `roundtrip.jsonl` | Each Dockerfile, whether it passed, and the first difference when not |
| `coverage.json` | Typed instructions over all instructions per Dockerfile, and how often each reason left an instruction untyped |

## Contributing

Changing the pipeline: [AGENTS.md](AGENTS.md).
