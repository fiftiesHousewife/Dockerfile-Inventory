#!/usr/bin/env bash
# Records the local JCR's replies (from scripts/lineage-demo.sh) to every request lineage's ArtifactoryHttp makes,
# as test fixtures: the repository list, each repository's catalogue, each image's tags, and for each tag the
# manifest (with its Content-Type and Docker-Content-Digest), the config blob and the storage API's file info.
#
# Bodies are saved byte for byte (a config blob's name is the digest of its bytes). index.tsv lists, per request,
# the path lineage asks for, the file, the status, the Content-Type and the Docker-Content-Digest, so a fake
# Artifactory in the tests can serve them. The token is made here, used and never written.
#
# Usage, from the repository root, with JCR running: scripts/record-jcr-fixtures.sh
# Settings: JCR_PORT (8082), ADMIN_PASSWORD ("password"), OUT (lineage/src/test/resources/fixtures/artifactory-jcr)
set -euo pipefail

JCR_PORT=${JCR_PORT:-8082}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-password}
OUT=${OUT:-lineage/src/test/resources/fixtures/artifactory-jcr}
API=http://localhost:$JCR_PORT/artifactory
PAGE=1000
MANIFEST_TYPES="application/vnd.docker.distribution.manifest.v2+json, \
application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, \
application/vnd.oci.image.index.v1+json"

die() { printf 'stopped: %s\n' "$*" >&2; exit 1; }
[[ -f gradlew ]] || die "run from the repository root"
[[ $(curl -s "$API/api/system/ping" || true) == OK ]] || die "JCR is not answering on port $JCR_PORT"

token=$(curl -s -u "admin:$ADMIN_PASSWORD" -X POST "$API/api/security/token" \
    -d username=admin -d scope=member-of-groups:readers -d expires_in=3600 | jq -r '.access_token // empty' || true)
[[ -n $token ]] || die "no token from JCR"

mkdir -p "$OUT"
rm -f "$OUT"/*.json "$OUT/index.tsv"
headers=$(mktemp)
trap 'rm -f "$headers"' EXIT

# get <path as lineage asks for it> <file> <Accept>
get() {
    curl -s -D "$headers" -o "$OUT/$2" -H "Authorization: Bearer $token" -H "Accept: $3" "$API/$1"
    local status type digest
    status=$(awk 'NR == 1 {print $2}' "$headers")
    type=$(awk 'tolower($1) == "content-type:" {sub(/\r$/, ""); $1 = ""; sub(/^ /, ""); print}' "$headers")
    digest=$(awk 'tolower($1) == "docker-content-digest:" {sub(/\r$/, ""); print $2}' "$headers")
    printf '/artifactory/%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$status" "$type" "$digest" >> "$OUT/index.tsv"
}
slug() { echo "$1" | tr '/:' '__'; }

get "api/repositories?type=local&packageType=docker" repositories.json application/json
for repository in $(jq -r '.[].key' "$OUT/repositories.json"); do
    get "api/docker/$repository/v2/_catalog?n=$PAGE" "catalog-$repository.json" application/json
    for image in $(jq -r '.repositories[]?' "$OUT/catalog-$repository.json"); do
        get "api/docker/$repository/v2/$image/tags/list?n=$PAGE" "tags-$(slug "$image").json" application/json
        for tag in $(jq -r '.tags[]?' "$OUT/tags-$(slug "$image").json"); do
            name=$(slug "$image:$tag")
            get "api/docker/$repository/v2/$image/manifests/$tag" "manifest-$name.json" "$MANIFEST_TYPES"
            config=$(jq -r '.config.digest // empty' "$OUT/manifest-$name.json")
            [[ -z $config ]] || get "api/docker/$repository/v2/$image/blobs/$config" "config-$name.json" '*/*'
            get "api/storage/$repository/$image/$tag" "storage-$name.json" application/json
        done
    done
done
echo "recorded $(wc -l < "$OUT/index.tsv" | tr -d ' ') replies into $OUT"
cut -f2,3,4 "$OUT/index.tsv" | column -t -s $'\t'
