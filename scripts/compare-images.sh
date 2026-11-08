#!/usr/bin/env bash
# Phase 5b: does the converter's rendering of a Dockerfile build the same image as the Dockerfile itself? For each
# chosen Dockerfile, both are built with Buildah from the same context at the commit discovery scanned, minutes
# apart, so on the same base, and the two images are compared: their config, and every file's path, type, mode,
# owner and content (timestamps never; the content of files that record their build time ignored, as listed).
#
#   1. renders each chosen Dockerfile through the converter (the faithful conversion, not a golden)
#   2. clones each Dockerfile's repository at the scanned commit
#   3. in a Buildah container: builds the original, with its directory as context or else the repository root,
#      then the regenerated Dockerfile with the same context, and lists each image's config and files
#   4. compares each pair: comparison.jsonl, one line per Dockerfile
#
# Usage, from the repository root, where Docker runs:
#   scripts/compare-images.sh
# Settings (environment): DISCOVERY_DIR (out/all/discovery), SHAS (scripts/compare-images.txt: the ten golden
# families' representatives), OUT (out/compare-images), BUILDAH_IMAGE. Bases behind a login (registry.redhat.io,
# registry.ci.openshift.org) need REGISTRY_AUTH_FILE pointing at an auth.json that can pull them; without it those
# builds fail and say so. Rerunning reuses the clones and Buildah's cache volume; remove them with
#   rm -rf out/compare-images && docker volume rm inventory-compare-buildah
set -euo pipefail

DISCOVERY_DIR=${DISCOVERY_DIR:-out/all/discovery}
SHAS=${SHAS:-scripts/compare-images.txt}
OUT=${OUT:-out/compare-images}
BUILDAH_IMAGE=${BUILDAH_IMAGE:-quay.io/buildah/stable:v1.37.0}
REGISTRY_AUTH_FILE=${REGISTRY_AUTH_FILE:-}

step() { printf '\n== %s\n' "$*"; }
die() { printf 'stopped: %s\n' "$*" >&2; exit 1; }

[[ -f gradlew ]] || die "run from the repository root"
[[ -f $DISCOVERY_DIR/manifest.jsonl ]] || die "no $DISCOVERY_DIR/manifest.jsonl; run discovery first"
[[ -f $SHAS ]] || die "no $SHAS: one blob SHA per line"
[[ -z $REGISTRY_AUTH_FILE || -f $REGISTRY_AUTH_FILE ]] || die "no $REGISTRY_AUTH_FILE"
for tool in docker git jq; do command -v $tool >/dev/null || die "$tool is not installed"; done
docker info >/dev/null 2>&1 || die "Docker is not running"
mkdir -p "$OUT/src"
OUT=$(cd "$OUT" && pwd)
DISCOVERY_DIR=$(cd "$DISCOVERY_DIR" && pwd)
SHAS=$(cd "$(dirname "$SHAS")" && pwd)/$(basename "$SHAS")
rm -rf "$OUT/work"

step "1. The converter's rendering of each Dockerfile"
STEP=render WORK_DIR="$OUT/work" DISCOVERY_DIR="$DISCOVERY_DIR" SHAS="$SHAS" ./gradlew -q :converter:compareImages

step "2. Each Dockerfile's repository at the scanned commit"
grep -v '^[[:space:]]*$' "$SHAS" | while read -r sha; do
    location=$(jq -r --arg sha "$sha" 'select(.blobSha == $sha) | [.repository, .commitSha, .path] | @tsv' \
        "$DISCOVERY_DIR/manifest.jsonl" | head -1)
    [[ -n $location ]] || { echo "no location for $sha" > "$OUT/work/$sha/failure.txt"; continue; }
    printf '%s\n' "$location" > "$OUT/work/$sha/location.tsv"
    IFS=$'\t' read -r repo commit path <<< "$location"
    echo "$sha  $repo  $path"
    dir=$OUT/src/$repo/$commit
    if [[ ! -d $dir/.git ]]; then
        git clone -q --filter=blob:none --no-checkout "https://github.com/$repo.git" "$dir"
        git -C "$dir" checkout -q "$commit"
        git -C "$dir" submodule update -q --init --recursive || true
    fi
done

step "3. Build both with Buildah and list each image"
cat > "$OUT/build.sh" <<'INNER'
set -uo pipefail
# The config Docker's format keeps (HEALTHCHECK, ONBUILD and SHELL included), and the files of the root
# filesystem: type, mode, owner, group, path, link target; then the sum of every regular file.
list() {
    local image=$1 into=$2 container mount
    mkdir -p "$into"
    buildah inspect --type image --format '{{json .Docker.Config}}' "$image" > "$into/config.json"
    container=$(buildah from -q "$image")
    mount=$(buildah mount "$container")
    (cd "$mount" && find . -xdev -printf '%y\t%m\t%U\t%G\t%p\t%l\n' > "$into/files.txt" \
        && find . -xdev -type f -print0 | xargs -0 -r sha256sum > "$into/sums.txt")
    buildah umount "$container" >/dev/null
    buildah rm "$container" >/dev/null
}
build() {
    buildah build -q --format docker --layers=false -f "$1" -t "$2" "$3" > "/work/$4.log" 2>&1
}
for work in /work/work/*/; do
    sha=$(basename "$work")
    [[ -f $work/failure.txt ]] && { echo "-- $sha: $(cat "$work/failure.txt")"; continue; }
    IFS=$'\t' read -r repo commit path < "$work/location.tsv"
    root=/src/$repo/$commit
    original=$root/$path
    context=$(dirname "$original")
    echo "-- $sha: $repo $path"
    if ! build "$original" "compare/$sha:original" "$context" "work/$sha/original-build"; then
        context=$root
        build "$original" "compare/$sha:original" "$context" "work/$sha/original-build" || {
            echo "the original does not build with its directory or the repository root as context; see" \
                "original-build.log" > "$work/failure.txt"; echo "   original does not build"; continue; }
    fi
    build "$work/regenerated.Dockerfile" "compare/$sha:regenerated" "$context" "work/$sha/regenerated-build" || {
        echo "the regenerated Dockerfile does not build where the original does; see regenerated-build.log" \
            > "$work/failure.txt"; echo "   regenerated does not build"; continue; }
    list "compare/$sha:original" "$work/original"
    list "compare/$sha:regenerated" "$work/regenerated"
    echo "   built and listed"
done
INNER
# Buildah must run natively: as an emulated binary it cannot re-exec itself for RUN.
native=linux/$(docker info --format '{{.Architecture}}' | sed 's/x86_64/amd64/; s/aarch64/arm64/')
auth=()
[[ -n $REGISTRY_AUTH_FILE ]] && auth=(-v "$(cd "$(dirname "$REGISTRY_AUTH_FILE")" && pwd)/$(basename "$REGISTRY_AUTH_FILE"):/auth.json:ro" -e REGISTRY_AUTH_FILE=/auth.json)
docker run --rm --privileged --platform "$native" -e STORAGE_DRIVER=vfs "${auth[@]+"${auth[@]}"}" \
    -v "$OUT/src:/src:ro" -v "$OUT:/work" -v inventory-compare-buildah:/var/lib/containers \
    "$BUILDAH_IMAGE" bash /work/build.sh | tee "$OUT/build.log"

step "4. Compare each pair"
STEP=compare WORK_DIR="$OUT/work" ./gradlew -q :converter:compareImages
mv "$OUT/work/comparison.jsonl" "$OUT/comparison.jsonl"
jq -r '[.blobSha[0:8], (if .built | not then "not built: " + .failure elif .same then "same"
        else "differs: " + ((.config + .files)[0:3] | join("; ")) end)] | join("  ")' "$OUT/comparison.jsonl"
echo
echo "same: $(jq -s 'map(select(.same)) | length' "$OUT/comparison.jsonl")," \
    "different: $(jq -s 'map(select(.built and (.same | not))) | length' "$OUT/comparison.jsonl")," \
    "not built: $(jq -s 'map(select(.built | not)) | length' "$OUT/comparison.jsonl")"
echo "output: $OUT/comparison.jsonl"
