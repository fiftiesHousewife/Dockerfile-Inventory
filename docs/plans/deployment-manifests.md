# Deployment manifests in the validator: plan

Status: planned 9 Nov; the questions answered 9 Nov.

## Why

The validator judges images from their Dockerfiles and lineage. What a pod really asks for is in its deployment:
the user and group it runs as (`securityContext.runAsUser`, `runAsNonRoot`, `fsGroup`), whether it is privileged
or adds capabilities, the host paths, ports and volumes it mounts, its probes. Two things wait for them
([openshift-validator](openshift-validator.md), answered 8 Nov: images first, manifests later):

- **refused**: on OpenShift an image alone is never refused (the SCC replaces its user), but a pod asking for more
  than its SCC allows (`privileged`, `runAsUser: 0`, an added capability, a host path) is not admitted;
- **the deployment's answer to information findings**: P9 (a `HEALTHCHECK` or `VOLUME` the deployment must declare as
  probes and volumes) and P3 (a low port the Service can map) are answered, or not, in the manifest.

## What exists

- Discovery fetches only Dockerfiles (`DockerfilePaths`), by blob SHA, at the released commit.
- The validator's findings are per Dockerfile; an image's verdict is its worst finding's severity.

## Design

- **Which manifests** (asked 9 Nov): plain workload YAML as committed (`Deployment`, `StatefulSet`, `DaemonSet`,
  `ReplicaSet`, `Job`, `CronJob`, `Pod`, OpenShift's `DeploymentConfig`; several documents a file). Helm templates
  and Kustomize overlays wait for later slices; a file holding `{{` is a template, counted as unread.
- **Discovery** fetches YAML beside Dockerfiles: every `.yaml` and `.yml` outside what is never deployed (the
  directories `DockerfilePaths` leaves out, a chart's `templates/`, CI configuration: `.github/`, `.tekton/`,
  `.gitlab-ci`, `.ci-operator.yaml`), stored once by blob SHA; their locations in a manifest file of their own
  (`yaml.jsonl`), so the Dockerfile stages read what they read today. Which files hold workloads is only known once
  read, so the facts stage keeps those that do.
- **Reading**: `Workloads` (in facts, with Jackson's YAML reader) turns each document into a workload: its kind,
  name and namespace, and for each container its image, ports, probes, volume mounts and security context, with the
  pod's security context, host namespaces and volumes. `workloads.jsonl`, one line per workload, with its location.
- **Linking** (asked 9 Nov: through lineage): a container's image reference is matched to a registry image lineage
  read, by digest, else by repository and tag, and lineage's credited link names the Dockerfile. No lineage, or no
  match: the workload is judged on its own pod settings, unlinked.
- **Checks**: a new severity, `refused` (the pod is not admitted), above `breaks`. Pod checks D1 to D6, severities in
  the profile like the others: under `restricted-v2`, `privileged` (D1), a fixed `runAsUser` (D2: admitted only
  inside the namespace's range, so inferred), a capability added beyond `NET_BIND_SERVICE` (D3),
  `allowPrivilegeEscalation: true` (D4), a host path, network, PID, IPC or port (D5), and an `Unconfined` seccomp
  profile (D6) are refused. A workload's manifest answers its image's P9 when it declares probes and mounts volumes
  at the image's `VOLUME` paths.
- **Verdicts** (asked 9 Nov: one per workload): images keep theirs; each workload gets its own, the worst of its pod
  findings and of its image's findings less those the manifest answers. The plan and the report show both.

## Slices

1. Reading workloads: `Workloads` on real manifests from the fixture bank (no network needed).
2. Discovery's YAML: the paths, the fetch, `yaml.jsonl`; facts writes `workloads.jsonl`. Measured on a survey run by
   the owner (this session's network does not reach GitHub).
3. The validator: `refused`, D1 to D6, the profiles, linking through lineage, the answers, workload verdicts.
4. The report: workloads on the OpenShift page.

## Questions answered

1. Plain workload YAML first; Helm and Kustomize later (9 Nov).
2. A container is tied to its Dockerfile through lineage only; otherwise it stays unlinked (9 Nov).
3. A verdict per workload, beside the images' (9 Nov).
