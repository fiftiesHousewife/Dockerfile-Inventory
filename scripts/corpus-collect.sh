#!/usr/bin/env bash
# Collects real Dockerfiles that broke something into the fixture bank (testdata/corpus/dockerfiles), each with the
# outcome the pipeline gave it, so the corpus tests pin it: every parse failure that is not a template, a few
# templates, every file with a RUN the shell reader cannot follow, every file that parses but fails the round trip,
# and up to PER_KIND files per kind of parser workaround (files that parse only on the retry).
#
# Usage, from the repository root:  scripts/corpus-collect.sh <run directory>
#   e.g. scripts/corpus-collect.sh out/all   (needs discovery/, facts/, converter/ and rules/ in it)
# Settings: PER_KIND (4), TEMPLATES (5), CORPUS (testdata/corpus/dockerfiles).
#
# Existing cases are kept as they are. A file with a secret-shaped value (rule S2, or a key, token or JWT shape
# anywhere) is never copied.
set -euo pipefail

run=${1:?usage: $0 <run directory>}
PER_KIND=${PER_KIND:-4}
TEMPLATES=${TEMPLATES:-5}
CORPUS=${CORPUS:-testdata/corpus/dockerfiles}
for need in discovery/manifest.jsonl facts/facts.jsonl facts/parse-failures.jsonl converter/roundtrip.jsonl \
            rules/findings.jsonl; do
    [[ -f $run/$need ]] || { echo "no $run/$need" >&2; exit 1; }
done
mkdir -p "$CORPUS"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

jq -r 'select(.rule == "S2") | .blobSha' "$run/rules/findings.jsonl" | sort -u > "$work/secret"
jq -r '[.blobSha, .repository, .path, .commitSha] | @tsv' "$run/discovery/manifest.jsonl" \
    | sort -u -t$'\t' -k1,1 > "$work/locations"
jq -r '[.blobSha, (if .passed then "passes" elif .difference == "the Dockerfile does not parse" then "not-parsed"
        else "fails" end)] | @tsv' "$run/converter/roundtrip.jsonl" | sort > "$work/roundtrip"
jq -r '[.blobSha, (if .retried then "retried" else "parsed" end), .facts.opaqueRuns] | @tsv' \
    "$run/facts/facts.jsonl" > "$work/parsed"
jq -r '[.blobSha, (if (.message | startswith("a template, not a Dockerfile")) then "template" else "failed" end), 0]
        | @tsv' "$run/facts/parse-failures.jsonl" > "$work/outcomes"
cat "$work/parsed" >> "$work/outcomes"
sort -o "$work/outcomes" "$work/outcomes"

secret_shaped() {
    grep -qx "$1" "$work/secret" \
        || grep -qE 'AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.' \
            "$run/discovery/blobs/$1"
}

# The workaround a retried file needs, from its text: the first that matches.
workaround() {
    local file=$1
    if grep -qE '\[.*"[[:space:]]+,' "$file"; then echo json-comma
    elif grep -qE -- '--[A-Za-z-]+=[^[:space:]]*\$\{[A-Za-z_][A-Za-z0-9_]*:-' "$file"; then echo flag-default
    elif grep -qE '^[[:space:]]*ARG[[:space:]].*[A-Za-z_][A-Za-z0-9_]*=([[:space:]]|$)' "$file"; then echo arg-empty
    elif grep -qE '^[[:space:]]*ARG[[:space:]]+[A-Za-z_][A-Za-z0-9_]*([[:space:]]+[A-Za-z_][A-Za-z0-9_=]*)+' "$file"
        then echo arg-names
    elif grep -qE '^[[:space:]]*\\[[:space:]]*$' "$file"; then echo lone-backslash
    elif awk 'prev ~ /\\[[:space:]]*$/ && /^[[:space:]]*#/ { found = 1 } { prev = $0 } END { exit !found }' "$file"
        then echo comment-in-continuation
    elif awk 'seen && /^[[:space:]]*#[[:space:]]*[A-Za-z_]+[[:space:]]*=/ { found = 1 }
              /^[[:space:]]*[A-Za-z]/ { seen = 1 } END { exit !found }' "$file"; then echo key-value-comment
    else echo other
    fi
}

# write <case name> <blob> <why>
write() {
    local name=$1 blob=$2 why=$3 dir=$CORPUS/$1
    [[ -d $dir ]] && return 0
    secret_shaped "$blob" && return 0
    local outcome runs trip repository path commit
    IFS=$'\t' read -r _ outcome runs < <(grep -m1 "^$blob"$'\t' "$work/outcomes")
    trip=$(grep -m1 "^$blob"$'\t' "$work/roundtrip" | cut -f2)
    IFS=$'\t' read -r _ repository path commit < <(grep -m1 "^$blob"$'\t' "$work/locations")
    mkdir -p "$dir"
    cp "$run/discovery/blobs/$blob" "$dir/Dockerfile"
    jq -n --arg repository "$repository" --arg path "$path" --arg commit "$commit" --arg blob "$blob" \
        --arg why "$why" --arg parse "$outcome" --argjson runs "${runs:-0}" --arg trip "${trip:-not-parsed}" \
        '{source: {repository: $repository, path: $path, commit: $commit, blobSha: $blob}, why: $why,
          parse: $parse, unreadableRuns: $runs, roundTrip: $trip}' > "$dir/expected.json"
    echo "added $name"
}

while IFS=$'\t' read -r blob outcome _; do
    [[ $outcome == failed ]] && write "failed-${blob:0:8}" "$blob" "rewrite-docker rejects it"
done < "$work/outcomes"

awk -F'\t' '$2 == "template"' "$work/outcomes" | head -n "$TEMPLATES" | while IFS=$'\t' read -r blob _ _; do
    write "template-${blob:0:8}" "$blob" "a template saved under a Dockerfile's name"
done

awk -F'\t' '$3 > 0' "$work/parsed" | while IFS=$'\t' read -r blob _ _; do
    write "unreadable-run-${blob:0:8}" "$blob" "a RUN the shell reader cannot follow"
done

awk -F'\t' '$2 == "fails"' "$work/roundtrip" | while IFS=$'\t' read -r blob _; do
    write "roundtrip-${blob:0:8}" "$blob" "parses but does not round-trip"
done

: > "$work/kinds"
awk -F'\t' '$2 == "retried"' "$work/parsed" | while IFS=$'\t' read -r blob _ _; do
    kind=$(workaround "$run/discovery/blobs/$blob")
    (( $(grep -cx "$kind" "$work/kinds" || true) < PER_KIND )) || continue
    secret_shaped "$blob" && continue
    echo "$kind" >> "$work/kinds"
    write "retried-$kind-${blob:0:8}" "$blob" "parses only with the parser's $kind workaround"
done

echo "corpus: $(find "$CORPUS" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ') cases"
