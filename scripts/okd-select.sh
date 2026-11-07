#!/usr/bin/env bash
# Picks the OKD images for openshift's lineage run (docs/plans/okd-lineage.md) from one release on quay.io, and
# writes what the next steps read. quay.io is read anonymously; no token is needed.
#
#   <out>/images.tsv     every component of the release: tag, source repository, commit
#   <out>/mirror.txt     each image to copy and its name in JCR (scripts/lineage-demo.sh with MIRROR=)
#   <out>/releases.csv   repository,commit of each chosen component (discovery's RELEASE_INDEX)
#
# Chosen: tools and network-tools (one built on the other), then components whose source repository discovery
# scanned, one per repository, spread evenly across the release by name, up to COUNT images with every base they are
# built FROM.
#
# Usage, from the repository root:  scripts/okd-select.sh [release] [output directory]
#   defaults: 5.0.0-okd-scos.5 out/okd
# Settings: DISCOVERY_DIR (out/openshift/discovery), COUNT (30).
set -euo pipefail

RELEASE=${1:-5.0.0-okd-scos.5}
OUT=${2:-out/okd}
DISCOVERY_DIR=${DISCOVERY_DIR:-out/openshift/discovery}
COUNT=${COUNT:-30}
CONTENT=okd/scos-content
ACCEPT='application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.list.v2+json,'
ACCEPT+='application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.v2+json'

die() { printf 'stopped: %s\n' "$*" >&2; exit 1; }
for tool in curl jq; do command -v $tool >/dev/null || die "$tool is not installed"; done
[[ -f $DISCOVERY_DIR/repos.jsonl ]] || die "no $DISCOVERY_DIR/repos.jsonl; run discovery on openshift first"
mkdir -p "$OUT"

fetch() { curl -sSfL --retry 5 --retry-all-errors --max-time 120 "$@"; }
token() { fetch "https://quay.io/v2/auth?service=quay.io&scope=repository:$1:pull" | jq -r .token; }
# The amd64 image config of <repository> <reference>, through a manifest list when there is one.
config() {
    local repository=$1 reference=$2 bearer manifest digest
    bearer=$(token "$repository")
    manifest=$(fetch -H "Authorization: Bearer $bearer" -H "Accept: $ACCEPT" \
        "https://quay.io/v2/$repository/manifests/$reference")
    digest=$(jq -r '.manifests // [] | map(select(.platform.architecture == "amd64")) | .[0].digest // empty' \
        <<<"$manifest")
    if [[ -n $digest ]]; then
        manifest=$(fetch -H "Authorization: Bearer $bearer" -H "Accept: $ACCEPT" \
            "https://quay.io/v2/$repository/manifests/$digest")
    fi
    fetch -H "Authorization: Bearer $bearer" \
        "https://quay.io/v2/$repository/blobs/$(jq -r .config.digest <<<"$manifest")"
}
# The name an image gets in JCR: no registry host, and a digest written as a tag.
jcr_name() {
    local reference=${1#*/}
    [[ $reference == *@sha256:* ]] && reference="${reference%@*}:sha256-${reference#*@sha256:}" \
        && reference=${reference:0:$(( ${#reference} - 52 ))}
    echo "$reference"
}

echo "== components of $RELEASE"
page=1
: > "$OUT/tags.txt"
while :; do
    reply=$(fetch "https://quay.io/api/v1/repository/$CONTENT/tag/?limit=100&page=$page&onlyActiveTags=true&filter_tag_name=like:$RELEASE-")
    jq -r '.tags[].name' <<<"$reply" >> "$OUT/tags.txt"
    [[ $(jq -r .has_additional <<<"$reply") == true ]] || break
    page=$(( page + 1 ))
done
sort -u -o "$OUT/tags.txt" "$OUT/tags.txt"
echo "$(wc -l < "$OUT/tags.txt" | tr -d ' ') components"

echo "== each component's source repository and commit"
mkdir -p "$OUT/configs"
while read -r tag; do
    file=$OUT/configs/$tag.json
    [[ -s $file ]] || config "$CONTENT" "$tag" > "$file" || { echo "could not read $tag"; rm -f "$file"; continue; }
    jq -r --arg tag "$tag" '[$tag,
        ((.config.Labels // {})["io.openshift.build.source-location"] // "" | sub("^https?://[^/]+/"; "")),
        ((.config.Labels // {})["io.openshift.build.commit.id"] // "")] | @tsv' "$file"
done < "$OUT/tags.txt" > "$OUT/images.tsv"

echo "== choosing up to $COUNT images"
scanned=$(jq -r 'select(.dockerfiles > 0) | .repository' "$DISCOVERY_DIR/repos.jsonl" | sort -u)
{
    grep -E $'^'"$RELEASE"$'-(tools|network-tools)\t' "$OUT/images.tsv" || true
    grep -vE $'^'"$RELEASE"$'-(tools|network-tools)\t' "$OUT/images.tsv" \
        | awk -F'\t' 'NR == FNR { scanned[$1] = 1; next } ($2 in scanned) && !seen[$2]++' <(echo "$scanned") - \
        | awk -v want="$COUNT" '{ line[NR] = $0 } END { step = NR / want; if (step < 1) step = 1
                                 for (i = 0; i * step < NR; i++) print line[int(i * step) + 1] }'
} > "$OUT/candidates.tsv"
: > "$OUT/chosen.tsv"
: > "$OUT/bases.txt"
while IFS=$'\t' read -r tag repository commit; do
    bases=$(jq -r '.history[].comment // empty | select(startswith("FROM ")) | sub("^FROM "; "")
        | select(. != "oci-archive")' "$OUT/configs/$tag.json")
    total=$(( $(wc -l < "$OUT/chosen.tsv") + 1 + $(sort -u "$OUT/bases.txt" <(echo "$bases") | grep -c . || true) ))
    (( total <= COUNT )) || break
    printf '%s\t%s\t%s\n' "$tag" "$repository" "$commit" >> "$OUT/chosen.tsv"
    echo "$bases" >> "$OUT/bases.txt"
done < "$OUT/candidates.tsv"
sort -u -o "$OUT/bases.txt" "$OUT/bases.txt"

{
    while read -r base; do
        [[ -n $base ]] && printf '%s %s\n' "$base" "$(jcr_name "$base")"
    done < "$OUT/bases.txt"
    cut -f1 "$OUT/chosen.tsv" | while read -r tag; do
        printf 'quay.io/%s:%s okd/%s:%s\n' "$CONTENT" "$tag" "${tag#"$RELEASE"-}" "$RELEASE"
    done
} > "$OUT/mirror.txt"
awk -F'\t' '$2 != "" && $3 != "" { print $2 "," $3 }' "$OUT/chosen.tsv" | sort -u > "$OUT/releases.csv"

echo "chosen: $(wc -l < "$OUT/chosen.tsv" | tr -d ' ') components and $(grep -c . "$OUT/bases.txt") bases"
column -t "$OUT/mirror.txt"
echo
echo "next: RELEASE_INDEX=$OUT/releases.csv scripts/discover.sh https://github.com/openshift $OUT/discovery"
