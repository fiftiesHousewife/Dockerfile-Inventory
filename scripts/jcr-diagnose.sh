#!/usr/bin/env bash
# Why the local JFrog Container Registry (started by lineage-demo.sh) is not answering: the image's architecture,
# Docker's memory, the container's state and memory use, and the errors in its own logs. Prints no secrets.
set -uo pipefail

JCR=inventory-jcr
IMAGE=${JCR_IMAGE:-releases-docker.jfrog.io/jfrog/artifactory-jcr:latest}
LOGS=/var/opt/jfrog/artifactory/log
JCR_PORT=${JCR_PORT:-8082}

section() { printf '\n== %s\n' "$*"; }

section "image architecture (arm64 runs natively on Apple silicon; amd64 is emulated)"
docker image inspect "$IMAGE" --format '{{.Architecture}}'

section "memory Docker may use (Artifactory wants 4 GB or more)"
docker info --format '{{.MemTotal}}' | awk '{printf "%.1f GB\n", $1 / 1024 / 1024 / 1024}'

section "container state"
docker ps -a --filter name=^$JCR$ --format '{{.Status}}'
docker stats --no-stream --format '{{.MemUsage}}  cpu {{.CPUPerc}}' $JCR

section "log files"
docker exec $JCR ls $LOGS

section "access-service.log, last 40 lines"
docker exec $JCR tail -40 $LOGS/access-service.log

section "errors in the service logs, last 20 (start-up retries left out)"
docker exec $JCR sh -c "grep -hiE 'error|exception|fatal' $LOGS/*-service.log | grep -v 'Cluster join' | tail -20"

section "reachability: from this Mac, from another container on the demo network, and inside JCR"
curl -s --noproxy '*' -m 5 -o /dev/null -w "from the Mac (127.0.0.1:$JCR_PORT): %{http_code}\n" \
    "http://127.0.0.1:$JCR_PORT/artifactory/api/system/ping"
docker run --rm --network inventory-demo curlimages/curl:8.10.1 -s -m 5 -o /dev/null \
    -w 'from the demo network (inventory-jcr:8082): %{http_code}\n' http://inventory-jcr:8082/artifactory/api/system/ping
docker exec $JCR curl -s -m 5 -o /dev/null -w 'inside JCR (localhost:8082): %{http_code}\n' \
    http://localhost:8082/artifactory/api/system/ping

section "what listens on port $JCR_PORT on this Mac (only Docker should)"
lsof -nP -iTCP:"$JCR_PORT" -sTCP:LISTEN

section "addresses JCR listens on inside its container (0.0.0.0 or :: means every interface)"
docker exec $JCR sh -c 'command -v ss >/dev/null && ss -ltn || cat /proc/net/tcp /proc/net/tcp6' | grep -E ':8082|:1F92' || true
