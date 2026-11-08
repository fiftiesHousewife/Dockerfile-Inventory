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
| 5. Convert existing Dockerfiles to builders | `converter` | Built, with the Java round trip; image round trip (5b) built as `./gradlew imageRoundTrip`, its first full run needs Docker |

The Dockerfile parser the pipeline uses is also a library of its own: see [parser/README.md](parser/README.md).

## Example reports

Dashboards from runs on public GitHub organisations, as the report stage writes them (private links; ask the owner
for access):

- [all six organisations combined](https://claude.ai/artifact/Npbva376tdsXBuHsaTko2B): 8,199 Dockerfile locations in
  961 repositories (sclorg, openshift, konflux-ci, redhat-developer, docker-library, bitnami), with OKD lineage, as the
  current linked pages (the smallest archetypes' detail pages are left out of this published copy for size)
- the earlier one-page dashboard: [openshift with lineage](https://claude.ai/artifact/6Uy3wKm1Wq9p2gwTdeGBPW): 2,919 Dockerfiles, with 30 of OKD's
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

`inventory <stage>` runs `discovery`, `facts`, `lineage`, `report`, `rules`, `validator` or `converter`, with the settings below
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
| `changes.jsonl` | One line per location: the last commit that changed the Dockerfile up to the scanned commit, and its date, both empty where GitHub has no history for it; a location GitHub refused (an error, counted in the summary) is asked for again on the next run |

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
shell. The Dockerfile parser is the project's own fork of OpenRewrite's, with the bugs found on real estates fixed
(`docs/upstream/rewrite-docker-issues.md`); a file it still rejects is listed in `parse-failures.jsonl` with the line
and the reason.

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
the Dockerfiles whose instructions match their build history, from the image's content alone (labels decide nothing):
an image's own steps are compared with the Dockerfiles built on its base and with those sharing one of its rare steps
(a step held by 20 Dockerfiles or fewer), which finds a Dockerfile even when a release pipeline recorded no base in the
image's history or built it on another. Without `ARTIFACTORY_URL` only the linking runs again, on what `OUT_DIR`
already holds.

| File | Contents |
| --- | --- |
| `images.jsonl`, `configs/` | Every tag, with the image config it names or why it was skipped; each config once |
| `lineage.jsonl` | Each image's parent: another image, none (a root), or several equally likely |
| `links.jsonl` | Each image's Dockerfiles: `STRONG` (exact, and at a released commit), `PROBABLE` (exact), `WEAK` (at least 80% of the steps align, with the best `score` and the `runnerUp`'s, the best of Dockerfiles with other steps), or `NONE`; and whether a root's Dockerfile names a base Artifactory holds |
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
export LINEAGE_DIR=/data/lineage              # optional: registry images, their chains and labels
export CONVERTER_DIR=/data/converter          # optional: the converter's OUT_DIR, for each Dockerfile's round trip
export VALIDATOR_DIR=/data/validator          # optional: the validator's OUT_DIR, for the action plan page
export STALE_AFTER_MONTHS=12                  # optional: an image unpublished this long is unmaintained
export LAYER_LIMIT=40                         # optional: an image with more layers is flagged
export FOOTPRINTS=/data/footprints.csv        # optional: footprint rules tried before the built-in ones
export LINK_THRESHOLD=0.8                     # optional: the score a weak link needs to credit an image (0 to 1)
export LINK_MARGIN=0.05                       # optional: how far it must beat the runner-up (0 to 1)
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
| `archetypes.jsonl` | One archetype per line, by size: its files, signature, a representative file and the repositories using it |
| `catalogue/dockerfiles.jsonl` | One line per distinct Dockerfile, described below |
| `index.html` | The summary page, described below; links to the pages below |
| `orgs/<org>.html` | The same summary for one organisation (the first part of each repository's name) |
| `hierarchy.html` | Every tree of estate Dockerfiles built on one another, from each root down, each node with its total layers, registry images, when it last changed and why it is unmaintained; how many stand alone |
| `blocked.html` | The unmaintained images in the middle of chains, the one holding back most images first, each with its reasons and the Dockerfiles whose images it holds back |
| `archetypes.html`, `archetypes-<n>.html` | Every archetype, ranked, 250 to a page, each named from what it builds and linked to the page of one of its Dockerfiles |
| `openshift.html` | With `VALIDATOR_DIR`: the action plan, how many images would break, drift or run under the validator's profile, the first 250 actions most urgent first, each linked to its Dockerfile's page, and the actions per repository; the summary then leads with the same sentence |
| `dockerfiles/<blobSha>.html` | One Dockerfile's catalogue entry made readable: its locations; the final image; its stages, copies and ownership changes; packages and runtimes; the Dockerfiles above and below it; its registry images with their chains; its actions (release scope, reach, conversion, base lag, near-duplicates); or why it did not parse |

Every page is drawn from `catalogue/dockerfiles.jsonl` (the archetype ranking from the same run), so the pages and
the catalogue cannot disagree. Each page stands alone (styles inside it, no scripts, nothing fetched), linked to the
others by relative links, so the folder can be copied or served as it is.

The summary counts the Dockerfiles, registry images, locations and organisations, and how many did not parse; how
many estate Dockerfiles each stands on and how many layers each carries, with those over `LAYER_LIMIT`; the
unmaintained images by reason and how many images stand on one; the final images running as root, exposing a port
below 1024 or shipping a toolchain (a compiler, build tool or `-devel` package in a final stage); the footprints; the
language runtimes, where each sits; and the unusual steps by kind. It links to the other pages and the data files.

The archetype pages leave templates, bundles, unreadable files and bases set by a variable out of the ranking, so
their count of archetypes covering 80% of locations differs from k80 in `passes.json`, which counts every location.

### The catalogue

`catalogue/dockerfiles.jsonl` describes each distinct Dockerfile (by blob SHA), parsed or not, for reporting on
what the estate builds. Secret-shaped values, and values under a secret's name, are `(hidden sha256:...)`.

| Field | Contents |
| --- | --- |
| `blobSha`, `locations` | The Dockerfile's identity, and every repository, path, scope and commit holding it, each with when the file last changed there (`lastChanged`, `lastChangedIn`) |
| `parseError` | Why it did not parse; the fields below are then empty |
| `image` | The final image as written: user and its kind, ports, entrypoint and command with their form, working directory, volumes, health check, shell, stop signal, environment and labels |
| `build.stages` | Per stage: its base (name, tag, digest, pinned) or the stage it builds on, the stages it copies from, its instructions, `RUN`s, commands, unreadable `RUN`s and layers, every copy with its origin (context, stage, image, URL, heredoc) and `--chown`/`--chmod`, every `chown`/`chgrp`/`chmod`, and unusual steps (`ADD`, `ONBUILD`, `VOLUME`, `RUN --mount`, ...) |
| `build.packages`, `build.runtimes` | Packages installed (manager, versioned, cleaned in the same `RUN`); Java, Go, Node.js and Python, each `final`, `build` (a build stage only) or `inherited` (from an estate image above it), with its evidence |
| `links` | The estate Dockerfiles that build its base (`parents`) and build on it (`children`), each from `lineage` or `inferred` with its evidence, and how many deep its chain is |
| `footprint` | The class of the base at its chain's root: micro, minimal, full, distroless, scratch, alpine, slim or unknown, by the footprint rules |
| `hierarchy.layers` | Its own, inherited and total layers, `counted` from its newest registry image or, without one, `declared` by the Dockerfiles; `overLimit` above `LAYER_LIMIT` |
| `hierarchy.images` | With `LINEAGE_DIR`: each registry image built from it (lineage linked it to this Dockerfile alone: an exact match, or a weak link to it alone scoring at least `LINK_THRESHOLD` and beating the runner-up by `LINK_MARGIN`), with its created and last-pushed times, its `chain` to the root (each image's own layers, times and reasons it is unmaintained) and `blockedBy`, the unmaintained images above it; its compressed `size` and `architectures` (an index's, else its config's); its `cadence`: pushes in the year before the registry's newest push, the median days between pushes, and how many days after its parent it was built; its `provenance` (signed, attested, with an SBOM, from cosign's tags beside it); its `usage` (downloads over its tags and the latest download); and its `labels`, each with its `source`: `dockerfile` (a `LABEL` in this Dockerfile), `build` (set when this image was built, not by its Dockerfile), `ancestor` or `base` (set above it, named in `setBy`), or `unknown` |
| `hierarchy.candidateImages` | Registry images lineage could not pin to this Dockerfile alone (several Dockerfiles, a tie, or a weak link below the threshold or within the margin) that this one may have built; they count nowhere else |
| `hierarchy.unmaintained` | `every repository holding it is archived`, when that is so |
| `actions.releaseScope` | `released` when any location is at a released commit, else `default branch only` |
| `actions.reach` | What fixing it reaches: the estate Dockerfiles built on it, directly or through others, their repositories and locations, and (with `LINEAGE_DIR`) the registry images built on its images |
| `actions.conversion` | With `CONVERTER_DIR`: the converter's text round trip, `passed` or `failed` with the first difference; else `not run` |
| `actions.baseLag` | With `LINEAGE_DIR`: the final base's tag and the newest tag of that image the registry holds, when it is later (compared as versions, between tags of the same shape: `9.4` and `9.6`, not `latest`) |
| `actions.nearDuplicates` | The other Dockerfiles in its near-duplicate group (the first 50 by blob SHA) and how many there are: candidates for one shared base or template |

The footprint rules are lines of `image,tag,footprint`, tried in order, the first match winning: a pattern without a
`/` matches the base's name, one with a `/` its path (registry aside), `*` matches any text, and an empty tag pattern
any tag. The built-in rules are in `report/src/main/resources/footprints.csv`; a `FOOTPRINTS` file's rules come first,
so an estate can name its own bases, for example `ocp/*,base-rhel*,full`.

An image is unmaintained when it was not rebuilt on its updated base, or was not pushed for `STALE_AFTER_MONTHS`
before the newest push in the registry (not before today, so a rerun on the same output gives the same answer).

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
| `rates.json` | Findings, files and locations per rule; the rules skipped |
| `samples/<rule>.csv` | Up to 50 findings per rule, spread across organisations, with a `verdict` column for people to classify before any fix is enabled |

`renovate/dockerfile-estate.json` is a Renovate preset for the central instance: Dockerfile bases pinned by
digest, base updates grouped into one pull request per repository.

## Judge the images for OpenShift

```bash
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export PROFILE=openshift-4-restricted-v2      # optional: a shipped profile's name or a profile file
export OUT_DIR=/data/validator
./gradlew :validator:run
```

The validator reads each Dockerfile's facts as the platform in `PROFILE` would run its image, and says what will
break, what drifts and what the deployment must supply. Two profiles ship, each describing every namespace as
OpenShift ships it, with no grants: `openshift-4-restricted-v2` (the default; OpenShift 4.11 and later) and
`openshift-4-restricted` (before 4.11, and clusters that kept it). A profile is a JSON file: copy one from
`validator/src/main/resources/profiles/`, change a check's severity (`breaks`, `drift`, `information` or `off`) or list
the registries the cluster pulls from in `allowedRegistries` (empty: any), and pass its path as `PROFILE`.

| Check | What it finds | Under v2 | Under v1 |
| --- | --- | --- | --- |
| P1 | a final user that is root, unset (inferred: it may come from the base) or named; OpenShift runs an arbitrary UID in group 0 | breaks | breaks |
| P2 | a `VOLUME`, `WORKDIR` or `HOME` never made writable by group 0 in the final stage, by `chmod`, `COPY --chmod` or sclorg's `fix-permissions` (inferred: another script may do it) | breaks | breaks |
| P3 | an exposed port below 1024 | breaks | breaks |
| P4 | `sudo`, `su`, `gosu` or `su-exec` in the start command, or `sudo` installed in the final image | breaks | breaks |
| P5 | a setuid bit set in the final stage | breaks | information |
| P6 | a shell-form `ENTRYPOINT` (or `CMD` without one): the process never receives SIGTERM | breaks | breaks |
| P7 | a base from a registry off the profile's list | breaks | breaks |
| P8 | a base not pinned by digest | drift | drift |
| P9 | a `HEALTHCHECK` (ignored) or a `VOLUME` (a plain directory): the deployment must declare probes and volumes | information | information |
| P10 | a start command reading the user's name or home (inferred) | information | information |

Metadata-only bundles (`FROM scratch` with no start command) never run as containers and are counted apart.

| File | Contents |
| --- | --- |
| `action-plan.json` | The profile, how many images were judged and how many not, the verdicts (`breaks`, `drift`, `information`, `runs`: each image's worst finding), the number of actions, and the actions per repository |
| `actions.jsonl` | One action per line, most urgent first (severity, then the locations it reaches, then read before inferred): the check, its severity and certainty, what to change, the Dockerfile's blob SHA, the findings behind it and every location |
| `verdicts.jsonl` | Each judged Dockerfile's verdict |

The report shows the plan as a page when given the validator's `OUT_DIR` as `VALIDATOR_DIR`.

`./gradlew validate` runs the validator over a stored estate and compares its counts with those committed in
`validator/src/test/resources/estate/expected.json` (outside `check`; it skips without the estate).

## Convert Dockerfiles to builder classes

```bash
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export OPT_IN=/data/opt-in.txt                # optional: owner/repo per line; only these get classes
export ALLOW_LIST=/data/approved-bases.csv    # optional: approved bases, for the TODO list
export OUT_DIR=/data/converter
export JAVA_ROUND_TRIP=true                   # default; false skips compiling a class per Dockerfile
./gradlew :converter:run
```

Every distinct Dockerfile is converted to the builder model faithfully, so the generated class reproduces the
image as it is, and is checked by a round trip: the rendered conversion must parse back to the same model and
have the same instructions as the original. A Dockerfile that already uses heredocs or `RUN --mount` is rendered
for a builder that has them. Policy breaches become `// TODO(<rule>)` comments, for later fixes. Then every
Dockerfile that passed has its class generated, compiled and run, and passes the Java round trip when the class
builds the same model and so the same Dockerfile.

| File | Contents |
| --- | --- |
| `generated/images/<owner>/<Name>Image.java` | One builder class per opted-in Dockerfile that passed the round trip |
| `roundtrip.jsonl` | Each Dockerfile, whether it passed, and the first difference when not; with the Java round trip, `javaPassed` and `javaDifference` |
| `coverage.json` | Typed instructions over all instructions per Dockerfile, how often each reason left an instruction untyped, and `javaRoundTrip`: classes that rebuilt their Dockerfile, of those tried |

### Round-trip tests on a real estate

Two tests check the converter against real Dockerfiles. Both read a discovery output, so they run as well on an
enterprise estate as on the public one.

They exist because the converter is what lets a team hand its Dockerfile over to the builders: the generated class
replaces the file, and every later fix is made by changing the class and regenerating. That is safe only if
regenerating changes nothing a running container can see. `roundTrip` checks the text across a whole estate: every
instruction understood, the rewritten Dockerfile the same instructions, the generated class rebuilding it.
`imageRoundTrip` checks what the text cannot show, by building the original and the regenerated Dockerfile and
comparing the images they make: the evidence a team needs before it opts in.

```bash
export ROUND_TRIP_DISCOVERY_DIR=out/all/discovery     # any discovery output (this is the default)
./gradlew roundTrip                                   # every Dockerfile in it converted; no Docker needed

export IMAGE_ROUND_TRIP_DOCKERFILES=my-dockerfiles.txt   # optional: which Dockerfiles to build (default below)
export GHE_API_URL=... GHE_TOKEN_FILE=...                # optional: where to clone them from, as for discovery
export REGISTRY_AUTH_FILE=~/auth.json                    # optional: a login for bases behind one
export REGISTRIES_CONF=~/registries.conf                 # optional: public names pulled through a mirror
export BUILD_TIMEOUT=10                                  # optional: minutes one build may take (default 10)
./gradlew imageRoundTrip                              # where Docker runs: built twice and compared
```

`roundTrip` converts every distinct Dockerfile and fails unless every instruction is typed, at least the committed
number of Dockerfiles pass the text round trip, and every passing Dockerfile's generated class rebuilds it.

`imageRoundTrip` takes the Dockerfiles listed in `IMAGE_ROUND_TRIP_DOCKERFILES`, one per line as `owner/repository
path/to/Dockerfile` or a blob SHA (`#` starts a comment); by default the ten in
`converter/src/test/resources/estate/image-round-trip.txt`, one per family of image. For each, the Dockerfile and
the converter's rendering of it are built with Buildah from the same context at the commit discovery scanned, and
the two images compared. The context is the Dockerfile's directory, else the repository root, else its directory
with the sources CI makes before the build stubbed as empty files in both builds (the next context is tried only
when a build failed finding a `COPY` or `ADD` source; any other failure, or a build over `BUILD_TIMEOUT`, leaves
the pair not built, saying why, and each pair's images are removed once it is done); each secret a `RUN` mounts is
given an empty one. The same image means the same config (labels the builder writes at build time aside) and the
same files: path, type, mode, owner, link target and content. Timestamps are never compared, nor the content of
files that record when they were built (package databases, logs, caches, Python bytecode, `/tmp`,
`/etc/machine-id`), nor the build container's hostname; those files are still checked to exist with the same mode
and owner. The original is also built a second time, as a control: a difference the two builds of the original
show too (a certificate generated at build time, a binary that embeds its build date) is set aside as noise and
listed, and only what remains counts. The test fails on any pair that built and differs; a pair that did
not build (a base behind a login, say) is reported, not failed. Images are pulled and built only, never pushed.

| File | Contents |
| --- | --- |
| `converter/build/image-round-trip/comparison.jsonl` | Per Dockerfile: built or why not, the same or not, each difference, the stubs built on |
| `converter/build/image-round-trip/work/<sha>/` | The regenerated Dockerfile and each build's log |

## Libraries

Three parts of the pipeline are published on their own, for Java 21 or newer, under `io.github.fiftieshousewife`:

| Library | What it does |
| --- | --- |
| [`dockerfile-parser`](parser/README.md) | A lossless Dockerfile parser and tree |
| [`dockerfile-model`](dsl/README.md) | An immutable Dockerfile model, a builder that enforces a base-image policy, and one renderer |
| [`dockerfile-reader`](reader/README.md) | A Dockerfile read into the model |

## Contributing

Changing the pipeline: [AGENTS.md](AGENTS.md).
