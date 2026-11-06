#!/usr/bin/env bash
# Why the local JFrog Container Registry (started by lineage-demo.sh) is not answering: the image's architecture,
# Docker's memory, the container's state and memory use, and the errors in its own logs. Prints no secrets.
set -uo pipefail

JCR=inventory-jcr
IMAGE=${JCR_IMAGE:-releases-docker.jfrog.io/jfrog/artifactory-jcr:latest}
LOGS=/var/opt/jfrog/artifactory/log

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
