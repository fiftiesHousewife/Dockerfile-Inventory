# Validator fixtures

- `copied-profile.json`: the shipped `openshift-4-restricted-v2` profile, copied and changed as an estate owner
  would: P8 (an unpinned base) raised from drift to breaks and one allowed registry added.
- `facts.jsonl`: thirteen real lines of the facts stage's output on the combined public estate (`out/all`, written 8 Nov
  with the catalogue's fields), each picked for one behaviour a check reads. By blob SHA, with one location each:

| Behaviour | Blob SHA | A location |
| --- | --- | --- |
| final user `root` | `007a290c...` | sclorg/origin `test/extended/testdata/builds/build-quota/Dockerfile` |
| no user set | `00b7e9b0...` | openshift/kubernetes-sigs-agent-sandbox `examples/pi-code-agent/Dockerfile` |
| a named user | `002742f1...` | openshift/ingress-node-firewall `Dockerfile` |
| a numeric user, `chgrp` and a working directory | `08bf2be5...` | openshift/custom-domains-operator `build/Dockerfile.olm-registry` |
| a numeric user, a `VOLUME`, no group-0 step | `141f718e...` | sclorg/rhscl-dockerfiles `rhel7.rh-postgresql95/Dockerfile` |
| a port below 1024 | `0e896df4...` | openshift/gssapi `test/docker/service/Dockerfile` |
| `sudo` installed | `03cab6f0...` | openshift/svt `openshift_performance/stac-a2/containers/rhel7-cuda9-stac-a2/Dockerfile` |
| a setuid bit (`chmod +s`) | `026d8695...` | openshift/moby-moby `contrib/nnp-test/Dockerfile` |
| a shell-form `ENTRYPOINT` | `080b0412...` | konflux-ci/multi-arch-konflux-example `Dockerfile` |
| a base pinned by digest | `01c98292...` | openshift/instaslice-operator `Dockerfile.scheduler.ocp` |
| a `HEALTHCHECK` | `2fc5b392...` | docker-library/healthcheck `cassandra/Dockerfile` |
| a metadata-only bundle (`FROM scratch`, no start command) | `02810ad6...` | redhat-developer/agentic-skills `Containerfile` |
| a start command reading `$HOME` or the user | `6cc3382e...` | openshift/cloud-api-adaptor `src/cloud-api-adaptor/test/e2e/fixtures/Dockerfile.testuser` |
- `manifest.jsonl`: discovery's manifest lines for those thirteen Dockerfiles on the same estate, up to two
  locations each.
