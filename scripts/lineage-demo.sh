#!/usr/bin/env bash
# Lineage, end to end, on a laptop: a local JFrog Container Registry (JCR), images built with Buildah from the
# Dockerfiles discovery found, then lineage, report and rules against them.
#
#   1. starts PostgreSQL (JCR 7.19x refuses its old built-in Derby) and JCR on it, and waits for JCR; creates the local Docker repository (or asks you to, in its UI)
#   2. clones each chosen Dockerfile's repository at the commit discovery scanned
#   3. mirrors the public CentOS Stream roots into JCR, then builds every chosen Dockerfile with Buildah for
#      linux/amd64 (lineage reads amd64 only), each FROM pointed at the image built before it, and pushes
#   4. runs lineage with an access token made in step 1 (held in this shell only), then report and rules
#
# Usage, from the repository root:
#   scripts/lineage-demo.sh                 # the chain: centos stream9 -> s2i-core -> s2i-base -> python 3.12, nginx
#   SCOPE=all scripts/lineage-demo.sh       # every Dockerfile.c9s discovery found (45 on sclorg; hours, emulated)
#   MIRROR=out/okd/mirror.txt DISCOVERY_DIR=out/okd/discovery FACTS_DIR=out/okd/facts OUT=out/okd \
#       scripts/lineage-demo.sh             # copy published images unchanged (scripts/okd-select.sh), no builds
#
# Settings (environment): DISCOVERY_DIR (out/demo4/discovery), FACTS_DIR (out/demo5/facts), OUT (out/lineage-demo),
# ACCEPT_JCR_EULA=yes (accepts JFrog's JCR licence agreement for you; read it first), JCR_PORT (the Mac's port, 8082), ADMIN_PASSWORD (JCR's first-start default, "password"), JCR_IMAGE, POSTGRES_IMAGE, BUILDAH_IMAGE,
# ARTIFACTORY_TOKEN (skips creating one). Rerunning is safe: running containers, an existing repository and
# existing clones are reused. The database password is a fixed local one: PostgreSQL is reachable only on the
# demo's Docker network. Remove everything afterwards:
#   docker rm -f inventory-jcr inventory-postgres && docker volume rm inventory-jcr inventory-postgres
set -euo pipefail

DISCOVERY_DIR=${DISCOVERY_DIR:-out/demo4/discovery}
FACTS_DIR=${FACTS_DIR:-out/demo5/facts}
OUT=${OUT:-out/lineage-demo}
SCOPE=${SCOPE:-chain}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-password}
# 7.77 (early 2024): 7.191 refused Derby, then left its Docker API at 503 when its licence check (GetEntitlements)
# failed. Every version from 7.77 on publishes arm64.
JCR_IMAGE=${JCR_IMAGE:-releases-docker.jfrog.io/jfrog/artifactory-jcr:7.77.17}
BUILDAH_IMAGE=${BUILDAH_IMAGE:-quay.io/buildah/stable:v1.37.0}
SKOPEO_IMAGE=${SKOPEO_IMAGE:-quay.io/skopeo/stable:v1.16.1}
MIRROR=${MIRROR:-}
POSTGRES_IMAGE=${POSTGRES_IMAGE:-postgres:16.4}
NETWORK=inventory-demo
JCR=inventory-jcr
POSTGRES=inventory-postgres
REPOSITORY=docker-local
REGISTRY=$JCR:8082/$REPOSITORY
JCR_PORT=${JCR_PORT:-8082}
API=http://localhost:$JCR_PORT/artifactory

step() { printf '\n== %s\n' "$*"; }
die() { printf 'stopped: %s\n' "$*" >&2; exit 1; }

[[ -f gradlew ]] || die "run from the repository root"
[[ -f $DISCOVERY_DIR/manifest.jsonl ]] || die "no $DISCOVERY_DIR/manifest.jsonl; run discovery first"
[[ -f $FACTS_DIR/facts.jsonl ]] || die "no $FACTS_DIR/facts.jsonl; run facts on $DISCOVERY_DIR first"
[[ -z $MIRROR || -f $MIRROR ]] || die "no $MIRROR; scripts/okd-select.sh writes it"
for tool in docker git jq curl; do command -v $tool >/dev/null || die "$tool is not installed"; done
docker info >/dev/null 2>&1 || die "Docker is not running"
memory_gb=$(docker info --format '{{.MemTotal}}' | awk '{printf "%d", $1 / 1024 / 1024 / 1024 + 0.5}')
(( memory_gb >= 4 )) || die "Docker may use ${memory_gb} GB; JCR needs 4 or more (Docker Desktop > Settings > \
Resources > Memory). If JCR already started short of memory: docker rm -f $JCR; docker volume rm $JCR"
mkdir -p "$OUT/src"
OUT=$(cd "$OUT" && pwd)

step "1. PostgreSQL and the JFrog Container Registry"
docker network inspect $NETWORK >/dev/null 2>&1 || docker network create $NETWORK >/dev/null
if [[ -z $(docker ps -q -f name=^$POSTGRES$) ]]; then
    docker rm -f $POSTGRES >/dev/null 2>&1 || true
    docker run -d --name $POSTGRES --network $NETWORK -v $POSTGRES:/var/lib/postgresql/data \
        -e POSTGRES_USER=artifactory -e POSTGRES_PASSWORD=artifactory -e POSTGRES_DB=artifactory \
        "$POSTGRES_IMAGE" >/dev/null
fi
printf 'waiting for PostgreSQL'
for _ in $(seq 60); do
    docker exec $POSTGRES pg_isready -q -U artifactory -d artifactory 2>/dev/null && break
    printf '.'; sleep 2
done
docker exec $POSTGRES pg_isready -q -U artifactory -d artifactory || die "PostgreSQL did not start; docker logs $POSTGRES"
echo " up"
# A JCR of another version cannot open this one's database, so its volumes have to go: that is left to you.
running_image=$(docker inspect -f '{{.Config.Image}}' $JCR 2>/dev/null || true)
[[ -z $running_image || $running_image == "$JCR_IMAGE" ]] || die "JCR runs $running_image, not $JCR_IMAGE. To start \
again on empty data: docker rm -f $JCR $POSTGRES; docker volume rm $JCR $POSTGRES"
# A JCR published on another port is started again on JCR_PORT; its data volume is kept.
if [[ -n $(docker ps -q -f name=^$JCR$) ]] && ! docker port $JCR 8082/tcp | grep -q ":$JCR_PORT\$"; then
    docker rm -f $JCR >/dev/null
fi
if [[ -z $(docker ps -q -f name=^$JCR$) ]]; then
    # lsof exits 1 when nothing listens, which is the good case.
    taken=$( (lsof -nP -iTCP:"$JCR_PORT" -sTCP:LISTEN 2>/dev/null || true) | awk 'NR > 1 {print $1 " (pid " $2 ")"}' \
        | sort -u)
    [[ -z $taken ]] || die "port $JCR_PORT is already taken by: $taken. Free it, or choose another: JCR_PORT=18082"
    docker rm -f $JCR >/dev/null 2>&1 || true
    docker run -d --name $JCR --network $NETWORK -p "$JCR_PORT:8082" \
        -v $JCR:/var/opt/jfrog/artifactory \
        -e JF_SHARED_DATABASE_TYPE=postgresql -e JF_SHARED_DATABASE_DRIVER=org.postgresql.Driver \
        -e JF_SHARED_DATABASE_URL=jdbc:postgresql://$POSTGRES:5432/artifactory \
        -e JF_SHARED_DATABASE_USERNAME=artifactory -e JF_SHARED_DATABASE_PASSWORD=artifactory \
        "$JCR_IMAGE" >/dev/null
fi
wait_for_jcr() {
    printf 'waiting for JCR to answer (a first start takes a few minutes)'
    for _ in $(seq 120); do
        [[ $(curl -s "$API/api/system/ping" || true) == OK ]] && break
        printf '.'; sleep 5
    done
    [[ $(curl -s "$API/api/system/ping" || true) == OK ]] || die "JCR did not start; scripts/jcr-diagnose.sh says why"
    echo " up"
}
wait_for_jcr

# True when JCR has a local Docker repository; REPOSITORY becomes its key (docker-local if there is one, as JCR's
# setup wizard may name its own differently).
repository_exists() {
    local key
    key=$(curl -s -u "admin:$ADMIN_PASSWORD" "$API/api/repositories?type=local&packageType=docker" \
        | jq -r --arg k "$REPOSITORY" 'if type == "array" then ([.[].key] | if index($k) then $k else .[0] end)
                                       else empty end // empty')
    [[ -n $key ]] || return 1
    REPOSITORY=$key
    REGISTRY=$JCR:8082/$REPOSITORY
}
curl -s -o /dev/null -w '%{http_code}' -u "admin:$ADMIN_PASSWORD" "$API/api/repositories" | grep -q 200 \
    || die "admin login refused; if you changed the admin password in the UI, set ADMIN_PASSWORD"
if ! repository_exists; then
    reply=$(curl -s -w ' (HTTP %{http_code})' -u "admin:$ADMIN_PASSWORD" -X PUT -H 'Content-Type: application/json' \
        "$API/api/repositories/$REPOSITORY" \
        -d "{\"key\":\"$REPOSITORY\",\"rclass\":\"local\",\"packageType\":\"docker\",\"dockerApiVersion\":\"V2\"}" || true)
    echo "creating $REPOSITORY through the API: $reply"
fi
if ! repository_exists; then
    # The free edition refuses the repositories API (Pro only), but its setup wizard reads this file at start and
    # creates the default repositories for each package type listed.
    echo "asking JCR's setup wizard for its Docker repositories (artifactory.config.import.yml), and restarting JCR"
    docker exec -i $JCR sh -c 'cat > /var/opt/jfrog/artifactory/etc/artifactory/artifactory.config.import.yml' <<YAML
version: 1
OnboardingConfiguration:
  repoTypes:
    - docker
YAML
    docker restart $JCR >/dev/null
    wait_for_jcr
fi
if ! repository_exists; then
    echo "JCR would not create the repository through its API. In http://localhost:$JCR_PORT/ui (admin / $ADMIN_PASSWORD):"
    echo "Administration > Repositories > Create a Repository > Local > Docker, key '$REPOSITORY'. Then press Enter."
    read -r || die "no keyboard to wait on (run with !?): create the repository in the UI, then rerun"
    repository_exists || die "still no local Docker repository named $REPOSITORY"
fi
echo "repository $REPOSITORY ready (images go to $REGISTRY)"

# JCR keeps its Docker API at 503 until its end-user licence agreement is accepted, normally at the first UI login.
# Accepting it is the user's call, so it happens only with ACCEPT_JCR_EULA=yes.
if curl -s -u "admin:$ADMIN_PASSWORD" "$API/api/docker/$REPOSITORY/v2/" | grep -q 'accept the EULA'; then
    [[ ${ACCEPT_JCR_EULA:-} == yes ]] || die "JCR's Docker API stays off until JFrog's JCR licence agreement is \
accepted. Read it at http://localhost:$JCR_PORT/ui (shown at the first login), then either accept it there or rerun \
with ACCEPT_JCR_EULA=yes to accept it through the API"
    curl -s -o /dev/null -u "admin:$ADMIN_PASSWORD" -X POST "$API/ui/jcr/eula/accept" || true
    echo "accepted JCR's licence agreement (ACCEPT_JCR_EULA=yes)"
fi

# Docker clients log in through this endpoint; just after a restart it answers 503 for a while.
printf 'waiting for the Docker API'
for _ in $(seq 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -u "admin:$ADMIN_PASSWORD" "$API/api/docker/$REPOSITORY/v2/token" || true)
    [[ $code == 200 ]] && break
    printf '.'; sleep 5
done
[[ $code == 200 ]] || die "the Docker API still answers $code; scripts/jcr-diagnose.sh may say why"
echo " up"

# Lineage reads with a bearer token. Made now, so a refusal shows before the long builds; never printed or saved.
can_read() {
    [[ $(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $1" "$API/api/repositories") == 200 ]]
}
if [[ -z ${ARTIFACTORY_TOKEN:-} ]]; then
    reply=$(curl -s -u "admin:$ADMIN_PASSWORD" -X POST -H 'Content-Type: application/json' \
        "http://localhost:$JCR_PORT/access/api/v1/tokens" -d '{"scope":"applied-permissions/admin","expires_in":86400}' || true)
    ARTIFACTORY_TOKEN=$(jq -r '.access_token // empty' <<<"$reply" 2>/dev/null || true)
    if [[ -z $ARTIFACTORY_TOKEN ]] || ! can_read "$ARTIFACTORY_TOKEN"; then
        echo "the access token API gave no usable token: $(jq -c 'del(.access_token, .refresh_token)' <<<"$reply" \
            2>/dev/null || echo "$reply" | head -c 300)"
        reply=$(curl -s -u "admin:$ADMIN_PASSWORD" -X POST "$API/api/security/token" \
            -d username=admin -d scope=member-of-groups:readers -d expires_in=86400 || true)
        ARTIFACTORY_TOKEN=$(jq -r '.access_token // empty' <<<"$reply" 2>/dev/null || true)
        [[ -n $ARTIFACTORY_TOKEN ]] && can_read "$ARTIFACTORY_TOKEN" || die "no usable token from the older API \
either: $(jq -c 'del(.access_token, .refresh_token)' <<<"$reply" 2>/dev/null || echo "$reply" | head -c 300). Create one \
in the UI (Administration > User Management > Access Tokens) and rerun with ARTIFACTORY_TOKEN set in your shell"
    fi
fi
can_read "$ARTIFACTORY_TOKEN" || die "ARTIFACTORY_TOKEN cannot list JCR's repositories"
export ARTIFACTORY_TOKEN
echo "access token ready (held in this shell only)"

# Published images copied as they are, digests kept, so lineage reads another build system's history: one line per
# image, its reference and its name in JCR. skopeo picks the amd64 image of a manifest list.
mirror_images() {
    step "2-3. Copy published images into JCR unchanged ($MIRROR)"
    : > "$OUT/build.log"
    while read -r source name; do
        [[ -n $source ]] || continue
        if docker run --rm --network $NETWORK "$SKOPEO_IMAGE" copy -q --preserve-digests \
                --override-os linux --override-arch amd64 --dest-tls-verify=false \
                --dest-creds "admin:$ADMIN_PASSWORD" "docker://$source" "docker://$REGISTRY/$name" </dev/null; then
            echo "pushed $REGISTRY/$name" | tee -a "$OUT/build.log"
        else
            echo "FAILED $source" | tee -a "$OUT/build.log"
        fi
    done < "$MIRROR"
}

if [[ -n $MIRROR ]]; then
    mirror_images
else
step "2. Dockerfiles to build, and their repositories at the scanned commit"
# One line per Dockerfile: image name, repository, commit, path, the base it is built FROM (as written).
plan=$OUT/plan.txt
jq -r 'select(.path | test("Dockerfile\\.c9s$")) | [.repository, .commitSha, .path, .blobSha] | @tsv' \
    "$DISCOVERY_DIR/manifest.jsonl" | sort -u | while IFS=$'\t' read -r repo commit path blob; do
    base=$(grep -m1 -i '^FROM' "$DISCOVERY_DIR/blobs/$blob" | awk '{print $2}')
    case "$repo:$path" in
        sclorg/s2i-base-container:core/Dockerfile.c9s) name=sclorg/s2i-core-c9s:c9s ;;
        sclorg/s2i-base-container:base/Dockerfile.c9s) name=sclorg/s2i-base-c9s:c9s ;;
        *) dir=$(dirname "$path"); part=${repo#sclorg/}; [[ $dir == . ]] || part+=-$dir
           name="sclorg/$(echo "$part" | tr 'A-Z/.' 'a-z--')-c9s:c9s" ;;
    esac
    printf '%s\t%s\t%s\t%s\t%s\n' "$name" "$repo" "$commit" "$path" "$base"
done > "$plan.all"
if [[ $SCOPE == all ]]; then
    cp "$plan.all" "$plan"
else
    grep -E $'\t(sclorg/s2i-base-container\t[0-9a-f]+\t(core|base)/|sclorg/s2i-python-container\t[0-9a-f]+\t3\\.12/|sclorg/nginx-container\t[0-9a-f]+\t1\\.24/)' \
        "$plan.all" > "$plan"
fi
# Parents before children: CentOS roots, then s2i-core's children, then s2i-base's.
rank() { case "$1" in *s2i-base*) echo 2 ;; *s2i-core*) echo 1 ;; *) echo 0 ;; esac; }
while IFS=$'\t' read -r name repo commit path base; do
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(rank "$base")" "$name" "$repo" "$commit" "$path" "$base"
done < "$plan" | sort -s -t$'\t' -k1,1n | cut -f2- > "$plan.ordered"
cut -f1,4 "$plan.ordered" | column -t
cut -f2,3 "$plan.ordered" | sort -u | while read -r repo commit; do
    dir=$OUT/src/$repo/$commit
    if [[ ! -d $dir/.git ]]; then
        git clone -q --filter=blob:none --no-checkout "https://github.com/$repo.git" "$dir"
        git -C "$dir" checkout -q "$commit"
    fi
    # sclorg's build contexts link into common/, a submodule (container-common-scripts) pinned by the commit.
    git -C "$dir" submodule update -q --init --recursive
done

step "3. Build linux/amd64 images with Buildah and push them to JCR"
# The image a FROM resolves to inside JCR: the public name without quay.io/, sclorg's tag defaulting to c9s.
cat > "$OUT/build.sh" <<'INNER'
set -uo pipefail
local_name() {
    local ref=${1#quay.io/}
    [[ $ref == *:* ]] || ref=$ref:c9s
    echo "$REGISTRY/$ref"
}
# JCR here speaks plain HTTP, so every pull and push needs --tls-verify=false. Buildah runs natively and builds
# for amd64 (lineage reads amd64 only); only the commands inside RUN are emulated.
login=(--tls-verify=false --creds "admin:$ADMIN_PASSWORD")
platform=(--platform linux/amd64)
for root in $(cut -f5 /work/plan.txt.ordered | grep '^quay.io/centos/' | sort -u); do
    echo "-- mirror $root"
    buildah pull -q "${platform[@]}" "$root" >/dev/null && buildah tag "$root" "$(local_name "$root")" \
        && buildah push -q "${login[@]}" "$(local_name "$root")" || echo "FAILED mirror $root"
done
while IFS=$'\t' read -r name repo commit path base; do
    echo "-- build $name from $repo $path"
    if buildah build -q --layers=false "${platform[@]}" "${login[@]}" --from "$(local_name "$base")" \
            -f "/src/$repo/$commit/$path" \
            -t "$REGISTRY/$name" "/src/$repo/$commit" >/dev/null \
        && buildah push -q "${login[@]}" "$REGISTRY/$name"; then
        echo "pushed $REGISTRY/$name"
    else
        echo "FAILED $name"
    fi
done < /work/plan.txt.ordered
INNER
# Buildah itself must run natively: as an emulated binary it cannot re-exec itself for RUN. Named explicitly, as
# Docker otherwise reuses whatever platform of the image it pulled before.
native=linux/$(docker info --format '{{.Architecture}}' | sed 's/x86_64/amd64/; s/aarch64/arm64/')
docker run --rm --privileged --platform "$native" --network $NETWORK \
    -e REGISTRY="$REGISTRY" -e ADMIN_PASSWORD="$ADMIN_PASSWORD" -e STORAGE_DRIVER=vfs \
    -v "$OUT/src:/src:ro" -v "$OUT:/work" -v inventory-demo-buildah:/var/lib/containers \
    "$BUILDAH_IMAGE" bash /work/build.sh | tee "$OUT/build.log"
fi

step "4. Lineage, then report and rules with it"
ARTIFACTORY_URL=$API FACTS_DIR=$FACTS_DIR DISCOVERY_DIR=$DISCOVERY_DIR OUT_DIR=$OUT/lineage CONCURRENCY=4 \
    ./gradlew -q :lineage:run
FACTS_DIR=$FACTS_DIR DISCOVERY_DIR=$DISCOVERY_DIR LINEAGE_DIR=$OUT/lineage OUT_DIR=$OUT/report ./gradlew -q :report:run
DISCOVERY_DIR=$DISCOVERY_DIR LINEAGE_DIR=$OUT/lineage OUT_DIR=$OUT/rules ./gradlew -q :rules:run

step "done"
echo "pushed: $(grep -c '^pushed' "$OUT/build.log") images, failed: $(grep -c '^FAILED' "$OUT/build.log" || true)"
echo "output: $OUT/lineage, $OUT/report/dashboard.html, $OUT/rules"
