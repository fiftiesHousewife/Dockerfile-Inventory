#!/usr/bin/env bash
# Lineage, end to end, on a laptop: a local JFrog Container Registry (JCR), images built with Buildah from the
# Dockerfiles discovery found, then lineage, report and rules against them.
#
#   1. starts JCR in Docker and waits for it; creates the local Docker repository (or asks you to, in its UI)
#   2. clones each chosen Dockerfile's repository at the commit discovery scanned
#   3. mirrors the public CentOS Stream roots into JCR, then builds every chosen Dockerfile with Buildah (inside a
#      linux/amd64 container, as lineage reads amd64 only), each FROM pointed at the image built before it, and pushes
#   4. creates an access token (held in this shell only) and runs lineage, then report and rules with LINEAGE_DIR
#
# Usage, from the repository root:
#   scripts/lineage-demo.sh                 # the chain: centos stream9 -> s2i-core -> s2i-base -> python 3.12, nginx
#   SCOPE=all scripts/lineage-demo.sh       # every Dockerfile.c9s discovery found (45 on sclorg; hours, emulated)
#
# Settings (environment): DISCOVERY_DIR (out/demo4/discovery), FACTS_DIR (out/demo5/facts), OUT (out/lineage-demo),
# ADMIN_PASSWORD (JCR's first-start default, "password"), JCR_IMAGE, BUILDAH_IMAGE, ARTIFACTORY_TOKEN (skips
# creating one). Rerunning is safe: a running JCR, an existing repository and existing clones are reused.
# Stop and remove JCR afterwards: docker rm -f inventory-jcr && docker volume rm inventory-jcr
set -euo pipefail

DISCOVERY_DIR=${DISCOVERY_DIR:-out/demo4/discovery}
FACTS_DIR=${FACTS_DIR:-out/demo5/facts}
OUT=${OUT:-out/lineage-demo}
SCOPE=${SCOPE:-chain}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-password}
JCR_IMAGE=${JCR_IMAGE:-releases-docker.jfrog.io/jfrog/artifactory-jcr:latest}
BUILDAH_IMAGE=${BUILDAH_IMAGE:-quay.io/buildah/stable:v1.37.0}
NETWORK=inventory-demo
JCR=inventory-jcr
REPOSITORY=docker-local
REGISTRY=$JCR:8082/$REPOSITORY
API=http://localhost:8082/artifactory

step() { printf '\n== %s\n' "$*"; }
die() { printf 'stopped: %s\n' "$*" >&2; exit 1; }

[[ -f gradlew ]] || die "run from the repository root"
[[ -f $DISCOVERY_DIR/manifest.jsonl ]] || die "no $DISCOVERY_DIR/manifest.jsonl; run discovery first"
[[ -f $FACTS_DIR/facts.jsonl ]] || die "no $FACTS_DIR/facts.jsonl; run facts on $DISCOVERY_DIR first"
for tool in docker git jq curl; do command -v $tool >/dev/null || die "$tool is not installed"; done
docker info >/dev/null 2>&1 || die "Docker is not running"
memory_gb=$(docker info --format '{{.MemTotal}}' | awk '{printf "%d", $1 / 1024 / 1024 / 1024 + 0.5}')
(( memory_gb >= 4 )) || die "Docker may use ${memory_gb} GB; JCR needs 4 or more (Docker Desktop > Settings > \
Resources > Memory). If JCR already started short of memory: docker rm -f $JCR; docker volume rm $JCR"
mkdir -p "$OUT/src"
OUT=$(cd "$OUT" && pwd)

step "1. JFrog Container Registry"
docker network inspect $NETWORK >/dev/null 2>&1 || docker network create $NETWORK >/dev/null
if [[ -z $(docker ps -q -f name=^$JCR$) ]]; then
    docker rm -f $JCR >/dev/null 2>&1 || true
    docker run -d --name $JCR --network $NETWORK -p 8081:8081 -p 8082:8082 \
        -v $JCR:/var/opt/jfrog/artifactory "$JCR_IMAGE" >/dev/null
fi
printf 'waiting for JCR to answer (a first start takes a few minutes)'
for _ in $(seq 120); do
    [[ $(curl -s "$API/api/system/ping" || true) == OK ]] && break
    printf '.'; sleep 5
done
[[ $(curl -s "$API/api/system/ping" || true) == OK ]] || die "JCR did not start; see: docker logs $JCR"
echo " up"

repository_exists() {
    curl -s -u "admin:$ADMIN_PASSWORD" "$API/api/repositories?type=local&packageType=docker" \
        | jq -e --arg k $REPOSITORY 'type == "array" and any(.[]; .key == $k)' >/dev/null
}
curl -s -o /dev/null -w '%{http_code}' -u "admin:$ADMIN_PASSWORD" "$API/api/repositories" | grep -q 200 \
    || die "admin login refused; if you changed the admin password in the UI, set ADMIN_PASSWORD"
if ! repository_exists; then
    curl -s -o /dev/null -u "admin:$ADMIN_PASSWORD" -X PUT -H 'Content-Type: application/json' \
        "$API/api/repositories/$REPOSITORY" \
        -d "{\"key\":\"$REPOSITORY\",\"rclass\":\"local\",\"packageType\":\"docker\",\"dockerApiVersion\":\"V2\"}" || true
fi
if ! repository_exists; then
    echo "JCR would not create the repository through its API. In http://localhost:8082/ui (admin / $ADMIN_PASSWORD):"
    echo "Administration > Repositories > Create a Repository > Local > Docker, key '$REPOSITORY'. Then press Enter."
    read -r
    repository_exists || die "still no local Docker repository named $REPOSITORY"
fi
echo "repository $REPOSITORY ready"

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
done

step "3. Build with Buildah (linux/amd64, emulated on Apple silicon) and push to JCR"
# The image a FROM resolves to inside JCR: the public name without quay.io/, sclorg's tag defaulting to c9s.
cat > "$OUT/build.sh" <<'INNER'
set -uo pipefail
local_name() {
    local ref=${1#quay.io/}
    [[ $ref == *:* ]] || ref=$ref:c9s
    echo "$REGISTRY/$ref"
}
login=(--tls-verify=false --creds "admin:$ADMIN_PASSWORD")
for root in $(cut -f5 /work/plan.txt.ordered | grep '^quay.io/centos/' | sort -u); do
    echo "-- mirror $root"
    buildah pull -q "$root" >/dev/null && buildah tag "$root" "$(local_name "$root")" \
        && buildah push -q "${login[@]}" "$(local_name "$root")" || echo "FAILED mirror $root"
done
while IFS=$'\t' read -r name repo commit path base; do
    echo "-- build $name from $repo $path"
    if buildah build -q --layers=false --from "$(local_name "$base")" -f "/src/$repo/$commit/$path" \
            -t "$REGISTRY/$name" "/src/$repo/$commit" >/dev/null \
        && buildah push -q "${login[@]}" "$REGISTRY/$name"; then
        echo "pushed $REGISTRY/$name"
    else
        echo "FAILED $name"
    fi
done < /work/plan.txt.ordered
INNER
docker run --rm --privileged --platform linux/amd64 --network $NETWORK \
    -e REGISTRY="$REGISTRY" -e ADMIN_PASSWORD="$ADMIN_PASSWORD" -e STORAGE_DRIVER=vfs \
    -v "$OUT/src:/src:ro" -v "$OUT:/work" -v inventory-demo-buildah:/var/lib/containers \
    "$BUILDAH_IMAGE" bash /work/build.sh | tee "$OUT/build.log"

step "4. Lineage, then report and rules with it"
if [[ -z ${ARTIFACTORY_TOKEN:-} ]]; then
    ARTIFACTORY_TOKEN=$(curl -s -u "admin:$ADMIN_PASSWORD" -X POST -H 'Content-Type: application/json' \
        http://localhost:8082/access/api/v1/tokens \
        -d '{"scope":"applied-permissions/admin","expires_in":86400}' | jq -r '.access_token // empty')
fi
[[ -n $ARTIFACTORY_TOKEN ]] || die "no token: create one in the UI (Administration > User Management > Access \
Tokens), then rerun with ARTIFACTORY_TOKEN set in your shell"
export ARTIFACTORY_TOKEN
ARTIFACTORY_URL=$API FACTS_DIR=$FACTS_DIR DISCOVERY_DIR=$DISCOVERY_DIR OUT_DIR=$OUT/lineage CONCURRENCY=4 \
    ./gradlew -q :lineage:run
FACTS_DIR=$FACTS_DIR DISCOVERY_DIR=$DISCOVERY_DIR LINEAGE_DIR=$OUT/lineage OUT_DIR=$OUT/report ./gradlew -q :report:run
DISCOVERY_DIR=$DISCOVERY_DIR LINEAGE_DIR=$OUT/lineage OUT_DIR=$OUT/rules ./gradlew -q :rules:run

step "done"
echo "built:  $(grep -c '^pushed' "$OUT/build.log") images, failed: $(grep -c '^FAILED' "$OUT/build.log" || true)"
echo "output: $OUT/lineage, $OUT/report/dashboard.html, $OUT/rules"
