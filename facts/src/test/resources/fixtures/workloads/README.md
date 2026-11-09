# Workload manifests

Real Kubernetes YAML from repositories the pipeline cloned for its image comparisons (9 Nov), each kept as written
unless noted:

| File | From | Shows |
| --- | --- | --- |
| `ptp-operator-manager.yaml` | openshift/ptp-operator `591eef78` `config/manager/manager.yaml` | a `Namespace` and a `Deployment` in one file; an image named `controller`, a placeholder Kustomize replaces |
| `bitwarden-cli-deployment.yaml` | openshift/external-secrets `088f9e5f` `docs/snippets/bitwarden-cli-deployment.yaml` | a `Deployment` with a port and three probes, beside a `Service` and a `NetworkPolicy`, wrapped in Liquid's `{% raw %}` for the docs site: unread |
| `1password-connect-server-deployment.yaml` | openshift/external-secrets `088f9e5f` `docs/snippets/1password-connect-server-deployment.yaml` | a documentation fragment: indented `...` lines where fields are left out: not YAML, unread |
| `ptp-daemon-template.yaml` | openshift/ptp-operator `591eef78` `bindata/linuxptp/ptp-daemon.yaml`, its first 40 lines | a Go template (`{{.Namespace}}`): unread |
