# Lineage: linking registry images to Dockerfiles

```bash
export ARTIFACTORY_URL=https://artifactory.example.com/artifactory/
export ARTIFACTORY_TOKEN=...                  # read access to every local Docker repository (or ARTIFACTORY_TOKEN_FILE)
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export OUT_DIR=/data/lineage                  # keep it between runs: a rerun resumes the Artifactory reading
export ARTIFACTORY_MAX_REQUESTS_PER_SECOND=5 # the most calls to Artifactory a second (or ARTIFACTORY_LOAD=gentle)
export ARTIFACTORY_CONCURRENCY=16            # optional: images read at once
export LINK_THRESHOLD=0.8                     # optional: the score a weak link needs to credit an image (0 to 1)
export LINK_MARGIN=0.05                       # optional: how far it must beat the runner-up (0 to 1)
docker-inventory lineage
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
| `links.jsonl` | Each image's Dockerfiles: `STRONG` (exact, and at a released commit), `PROBABLE` (exact), `WEAK` (at least 80% of the steps align, with the best `score` and the `runnerUp`'s, the best of Dockerfiles with other steps), or `NONE`; whether a root's Dockerfile names a base Artifactory holds; and whether it is `credited`: the image was built from this Dockerfile alone (an exact match, or a weak link to it alone scoring at least `LINK_THRESHOLD` and beating the runner-up by `LINK_MARGIN`). The report and the validator count only credited links |
| `effective.jsonl` | Each image's user, environment, labels, working directory, entrypoint and command, and which image in its chain set each one |
| `gaps.jsonl` | Images built here that no scanned Dockerfile matches |
| `external-bases.jsonl` | Unmatched roots: external bases mirrored into Artifactory |
| `outdated-bases.jsonl` | Images built on a base that has since been pushed again under the same name |
| `rates.json` | Tags, images, skips, lineage coverage, links by tier, gaps |

## Trying lineage without an Artifactory

`./gradlew lineageDemo` starts PostgreSQL and a local JFrog Container Registry (JCR 7.77) in Docker, builds images with
Buildah from the CentOS Stream 9 Dockerfiles that discovery found (each at its scanned commit, each `FROM` pointed at
the image built before it), pushes them, and runs lineage, the report and the rules against them. It needs Docker with 4
GB of memory or more and git, and a discovery and facts run to start from; `LineageDemo`'s Javadoc lists its settings.
Rerun, it reuses the running containers, the repository and the clones; when JCR will not make the repository, it says
how to in the UI, and a rerun goes on. By default it builds one chain (CentOS Stream 9, `s2i-core`, `s2i-base`, Python
3.12, nginx 1.24); `SCOPE=all` builds every `Dockerfile.c9s`, which takes hours when amd64 is emulated.

On a first run JCR's Docker API stays off until JFrog's JCR licence agreement is accepted: read it, then run with
`ACCEPT_JCR_EULA=yes` to accept it through the API. If another program holds port 8082, set `JCR_PORT`.
`./gradlew jcrDiagnose` shows why JCR is not answering: memory, its logs, which ports answer, and what its Docker
API replies.

To try lineage on images someone else built, OKD's published images can be copied in unchanged instead:

```bash
./gradlew selectOkd                         # one OKD release on quay.io -> out/okd/mirror.txt, releases.csv
GHE_API_URL=https://github.com/openshift GHE_TOKEN=$(env -u GITHUB_TOKEN gh auth token) \
    RELEASE_INDEX=out/okd/releases.csv OUT_DIR=out/okd/discovery docker-inventory discovery
DISCOVERY_DIR=out/okd/discovery OUT_DIR=out/okd/facts docker-inventory facts
MIRROR=out/okd/mirror.txt DISCOVERY_DIR=out/okd/discovery FACTS_DIR=out/okd/facts OUT=out/okd \
    ./gradlew lineageDemo
```

`selectOkd` (`--args="<release> <output directory>"`, default `5.0.0-okd-scos.5 out/okd`) picks about 30 images (the
bases, `tools` and `network-tools`, and components whose repository discovery found, one per repository) and writes each
chosen component's repository and the commit its source label names, so discovery reads the Dockerfiles that built them.
The mirror copies with skopeo, keeping digests.
