# Workload manifests

Real Kubernetes YAML from repositories the pipeline cloned for its image comparisons (9 Nov), each kept as written
unless noted:

| File | From | Shows |
| --- | --- | --- |
| `ptp-operator-manager.yaml` | openshift/ptp-operator `591eef78` `config/manager/manager.yaml` | a `Namespace` and a `Deployment` in one file; an image named `controller`, a placeholder Kustomize replaces |
| `bitwarden-cli-deployment.yaml` | openshift/external-secrets `088f9e5f` `docs/snippets/bitwarden-cli-deployment.yaml` | a `Deployment` with a port and three probes, beside a `Service` and a `NetworkPolicy`, wrapped in Liquid's `{% raw %}` for the docs site: unread |
| `1password-connect-server-deployment.yaml` | openshift/external-secrets `088f9e5f` `docs/snippets/1password-connect-server-deployment.yaml` | a documentation fragment: indented `...` lines where fields are left out: not YAML, unread |
| `ptp-daemon-template.yaml` | openshift/ptp-operator `591eef78` `bindata/linuxptp/ptp-daemon.yaml`, its first 40 lines | a Go template (`{{.Namespace}}`): unread |
| `openshift-tools-sync.yaml` | openshift/openshift-tools `e342f665` `ansible/roles/openshift_node_group/files/sync.yaml`, from the survey of 9 Nov | a `DaemonSet` asking for everything restricted-v2 refuses: privileged, `runAsUser: 0`, the host's network, PID and paths; its image a blank placeholder (`' '`) Ansible fills |
| `image-inspector-app.yaml` | openshift/image-inspector `c5b06d18` `kubernetes/image-inspector-app.yaml`, from the survey of 9 Nov | a privileged `Pod` mounting the host's Docker socket |

