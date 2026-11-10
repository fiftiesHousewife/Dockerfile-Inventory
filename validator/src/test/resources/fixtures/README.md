# Validator fixtures

- `copied-profile.json`: the shipped `openshift-4-restricted-v2` profile, copied and changed as an estate owner
  would: P8 (an unpinned base) raised from drift to breaks and one allowed registry added; P11 and D7 as shipped.
- `facts.jsonl`: fifteen real lines of the facts stage's output on the combined public estate (`out/all`, written 8 Nov
  with the catalogue's fields), each picked for one behaviour a check reads. By blob SHA, with one location each:

| Behaviour | Blob SHA | A location |
| --- | --- | --- |
| final user `root` | `007a290c...` | sclorg/origin `test/extended/testdata/builds/build-quota/Dockerfile` |
| no user set | `00b7e9b0...` | openshift/kubernetes-sigs-agent-sandbox `examples/pi-code-agent/Dockerfile` |
| a named user | `002742f1...` | openshift/ingress-node-firewall `Dockerfile` |
| a numeric user, `chgrp` and a working directory | `08bf2be5...` | openshift/custom-domains-operator `build/Dockerfile.olm-registry` |
| a numeric user, a `VOLUME`, made group-writable by sclorg's `fix-permissions` script | `141f718e...` | sclorg/rhscl-dockerfiles `rhel7.rh-postgresql95/Dockerfile` |
| two `VOLUME`s and a `WORKDIR`, no group-0 step | `12baa7e1...` | redhat-developer/message-tagging-service `Dockerfile` |
| a port below 1024 | `0e896df4...` | openshift/gssapi `test/docker/service/Dockerfile` |
| `sudo` installed | `03cab6f0...` | openshift/svt `openshift_performance/stac-a2/containers/rhel7-cuda9-stac-a2/Dockerfile` |
| a setuid bit (`chmod +s`) | `026d8695...` | openshift/moby-moby `contrib/nnp-test/Dockerfile` |
| a shell-form `ENTRYPOINT` | `080b0412...` | konflux-ci/multi-arch-konflux-example `Dockerfile` |
| a base pinned by digest | `01c98292...` | openshift/instaslice-operator `Dockerfile.scheduler.ocp` |
| a `HEALTHCHECK` | `2fc5b392...` | docker-library/healthcheck `cassandra/Dockerfile` |
| a metadata-only bundle (`FROM scratch`, no start command) | `02810ad6...` | redhat-developer/agentic-skills `Containerfile` |
| a start command reading `$HOME` or the user | `6cc3382e...` | openshift/cloud-api-adaptor `src/cloud-api-adaptor/test/e2e/fixtures/Dockerfile.testuser` |
| `fix-permissions ${APP_ROOT}`, a variable set by `ENV` | `d133a9d4...` | sclorg/s2i-python-container `3.12-minimal/Dockerfile.rhel10` |
- `manifest.jsonl`: discovery's manifest lines for those fifteen Dockerfiles on the same estate, up to two
  locations each.
- `workloads.jsonl`: three real lines of the facts stage's `workloads.jsonl` from the owner's survey of
  `github.com/openshift` (9 Nov, `out/openshift-try`):

| Behaviour | Blob SHA | Location |
| --- | --- | --- |
| a plain Deployment, nothing asked of the host | `a185638a...` | openshift/training `assets/cluster-filler.yaml` |
| a privileged DaemonSet running as root, on the host's network, PID and paths | `d88374c0...` | openshift/openshift-tools `ansible/roles/openshift_node_group/files/sync.yaml` |
| a privileged Pod mounting a host path (the Docker socket) | `fe15041e...` | openshift/image-inspector `kubernetes/image-inspector-app.yaml` |
- `lineage/`: lineage's output in the shapes `lineage` writes (trimmed to the fields the validator reads), made by
  hand to link the workloads above to the fixture Dockerfiles: `openshift/image-inspector:latest` credited to the
  `HEALTHCHECK` image `2fc5b392...`, `team/postgres:9.6` credited to the `VOLUME` image `141f718e...`, `team/app:1.2`
  linked to `12baa7e1...` but not credited, and `rhel7:7.9` skipped (no config digest).
