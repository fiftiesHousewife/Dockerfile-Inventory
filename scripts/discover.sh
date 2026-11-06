#!/usr/bin/env bash
# Runs discovery on one GitHub organisation with the token of the GitHub CLI's login, so no long command line has to
# be typed or pasted. The token goes to discovery through the environment only; it is never printed or saved.
#
# Usage, from the repository root:  scripts/discover.sh <organisation URL> <output directory>
#   e.g. scripts/discover.sh https://github.com/openshift out/openshift/discovery
# Settings: CONCURRENCY (8). A GITHUB_TOKEN in the environment is ignored in favour of the CLI's login.
set -euo pipefail

[[ $# -eq 2 ]] || { echo "usage: $0 <organisation URL> <output directory>" >&2; exit 2; }
[[ -f gradlew ]] || { echo "run from the repository root" >&2; exit 2; }
token=$(env -u GITHUB_TOKEN gh auth token) || { echo "not logged in: gh auth login" >&2; exit 1; }

GHE_API_URL=$1 GHE_TOKEN=$token OUT_DIR=$2 CONCURRENCY=${CONCURRENCY:-8} ./gradlew -q :discovery:run
