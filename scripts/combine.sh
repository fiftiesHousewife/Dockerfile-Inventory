#!/usr/bin/env bash
# Merges several discovery outputs into one, so facts, rules, the converter and the report can run on all of them as
# one estate. Locations (manifest.jsonl) and repositories (repos.jsonl) are concatenated; a Dockerfile held by more
# than one source is stored once, by its blob SHA. A repository should come from one source only: discover each
# organisation once.
#
# Usage, from the repository root:  scripts/combine.sh <output discovery directory> <discovery directory>...
#   e.g. scripts/combine.sh out/all/discovery out/okd/discovery out/bitnami/discovery
set -euo pipefail

[[ $# -ge 3 ]] || { echo "usage: $0 <output discovery directory> <discovery directory> <discovery directory>..." >&2
    exit 2; }
out=$1
shift
for source in "$@"; do
    [[ -f $source/manifest.jsonl && -f $source/repos.jsonl && -d $source/blobs ]] \
        || { echo "not a discovery output: $source" >&2; exit 1; }
done
mkdir -p "$out/blobs"

cat "${@/%//manifest.jsonl}" | awk '!seen[$0]++' > "$out/manifest.jsonl"
cat "${@/%//repos.jsonl}" | awk '!seen[$0]++' > "$out/repos.jsonl"
for source in "$@"; do
    find "$source/blobs" -type f ! -name '*.part' -exec cp -n {} "$out/blobs/" \;
done

repeated=$(jq -r .repository "$out/repos.jsonl" | sort | uniq -d | head -5)
[[ -z $repeated ]] || echo "warning: repositories in more than one source (counted twice): $repeated" >&2
echo "combined $# sources: $(wc -l < "$out/manifest.jsonl" | tr -d ' ') locations, \
$(wc -l < "$out/repos.jsonl" | tr -d ' ') repositories, $(find "$out/blobs" -type f | wc -l | tr -d ' ') files"
