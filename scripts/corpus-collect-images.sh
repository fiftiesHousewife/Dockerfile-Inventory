#!/usr/bin/env bash
# Collects real images into the fixture bank (testdata/corpus/images) from a lineage run, each with the facts of every
# Dockerfile in the repository its source label names (so the linker must pick the right one among them) and the
# link lineage made, so lineage's corpus test pins it. Images without a source label, or whose repository the run's
# discovery did not hold, are collected with no candidates and must stay unlinked.
#
# Usage, from the repository root:  scripts/corpus-collect-images.sh <run directory>
#   e.g. scripts/corpus-collect-images.sh out/okd   (needs lineage/, facts/ and discovery/ in it)
# A case is named from the image's path and the start of its config digest (okd-tools-1a2b3c4d).
# Existing cases are kept as they are.
set -euo pipefail

run=${1:?usage: $0 <run directory>}
CORPUS=${CORPUS:-testdata/corpus/images}
for need in lineage/images.jsonl lineage/links.jsonl facts/facts.jsonl discovery/manifest.jsonl; do
    [[ -f $run/$need ]] || { echo "no $run/$need" >&2; exit 1; }
done
mkdir -p "$CORPUS"

jq -r 'select(.configDigest != null) | [.configDigest, (.image | split("/") | .[-2:] | join("-"))] | @tsv' \
    "$run/lineage/images.jsonl" | sort -u -t$'\t' -k1,1 | while IFS=$'\t' read -r digest name; do
    case=$name-${digest:7:8}
    dir=$CORPUS/$case
    [[ -d $dir ]] && continue
    config=$run/lineage/configs/${digest/:/_}.json
    [[ -f $config ]] || continue
    repository=$(jq -r '(.config.Labels // {})["io.openshift.build.source-location"]
        // (.config.Labels // {})["org.opencontainers.image.source"] // ""
        | sub("^https?://[^/]+/"; "") | split("/") | .[0:2] | join("/")' "$config")
    mkdir -p "$dir"
    jq '{config: {Labels: (.config.Labels // {}), User: .config.User}, rootfs, history}' "$config" > "$dir/config.json"
    jq -c --arg repository "$repository" 'select(.repository == $repository) | .blobSha' \
        "$run/discovery/manifest.jsonl" | sort -u > "$dir/.blobs"
    jq -c --slurpfile blobs <(jq -s '.' "$dir/.blobs") --arg repository "$repository" \
        'select(.blobSha as $b | $blobs[0] | index($b)) | {blobSha, steps, fromPath, repository: $repository}' \
        "$run/facts/facts.jsonl" > "$dir/dockerfiles.jsonl"
    rm "$dir/.blobs"
    jq --arg digest "$digest" --arg repository "$repository" --arg name "$name" \
        'select(.configDigest == $digest) | {image: $name, sourceRepository: $repository, tier, blobShas}' \
        "$run/lineage/links.jsonl" > "$dir/expected.json"
    echo "added $case ($(wc -l < "$dir/dockerfiles.jsonl" | tr -d ' ') candidate Dockerfiles)"
done
