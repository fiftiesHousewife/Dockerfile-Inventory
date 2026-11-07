#!/usr/bin/env bash
# Releases the Dockerfile parser (io.github.fiftieshousewife:dockerfile-parser) to Maven Central: builds and signs the
# bundle with Gradle, zips it, and uploads it to the Central Portal as user-managed, so Central validates it and waits
# for you to press Publish at https://central.sonatype.com/publishing/deployments. Nothing is public until then.
#
# Usage, from the repository root, on a clean main with the version set (inventory.parserVersion in
# gradle.properties):  scripts/release-parser.sh
# Needs in the environment (never in a file in the repository):
#   SIGNING_KEY, SIGNING_PASSWORD             the ASCII-armoured private GPG key and its passphrase
#   CENTRAL_TOKEN_USER, CENTRAL_TOKEN_PASSWORD a Central Portal user token
# Tags the release parser-v<version> after a successful upload; push the tag yourself.
set -euo pipefail

die() { printf 'stopped: %s\n' "$*" >&2; exit 1; }
[[ -f gradlew ]] || die "run from the repository root"
for name in SIGNING_KEY SIGNING_PASSWORD CENTRAL_TOKEN_USER CENTRAL_TOKEN_PASSWORD; do
    [[ -n ${!name:-} ]] || die "$name is not set"
done
[[ -z $(git status --porcelain) ]] || die "the working tree has changes; release from a clean checkout"
version=$(sed -n 's/^inventory.parserVersion=//p' gradle.properties)
[[ -n $version && $version != *SNAPSHOT* ]] || die "inventory.parserVersion is not a release version: '$version'"
git rev-parse -q --verify "refs/tags/parser-v$version" >/dev/null && die "parser-v$version is already tagged"

rm -rf parser/build/central
./gradlew -q :parser:check
bundle_dir=parser/build/central
find "$bundle_dir" -name '*.asc' | grep -q . || die "no signatures in $bundle_dir: was SIGNING_KEY read?"
bundle=parser/build/dockerfile-parser-$version-bundle.zip
rm -f "$bundle"
(cd "$bundle_dir" && find . -type f ! -name 'maven-metadata.xml*' | sort | zip -q -@ "../$(basename "$bundle")")

token=$(printf '%s:%s' "$CENTRAL_TOKEN_USER" "$CENTRAL_TOKEN_PASSWORD" | base64 | tr -d '\n')
deployment=$(curl -sSf -X POST \
    -H "Authorization: Bearer $token" \
    -F "bundle=@$bundle" \
    "https://central.sonatype.com/api/v1/publisher/upload?name=dockerfile-parser-$version&publishingType=USER_MANAGED")
git tag -a "parser-v$version" -m "Dockerfile parser $version"
echo "uploaded: deployment $deployment"
echo "next: check and publish it at https://central.sonatype.com/publishing/deployments, then git push origin parser-v$version"
