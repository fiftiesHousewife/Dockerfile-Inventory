# Deployment manifests in the validator: plan

Status: planned 9 Nov; the questions answered 9 Nov; slices 1 and 2 built 9 Nov.

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
  and Kustomize overlays wait for later slices; a file holding `{{` or `{%` is a template, counted as unread.
- **Discovery** fetches YAML beside Dockerfiles: every `.yaml` and `.yml` outside what is never deployed (the
  directories `DockerfilePaths` leaves out; tests, documentation, examples and samples, asked 9 Nov after measuring;
  a chart's `templates/`, `Chart.yaml` and values; CI configuration: `.github/`, `.tekton/`, `.circleci/`,
  `.gitlab-ci`, `.ci-operator.yaml`), stored once by blob SHA in `yaml/`, apart from the Dockerfiles' `blobs/`; their locations in a manifest file of their own
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

## Built, slice 1 (9 Nov)

`Workloads.read` (facts, Jackson's YAML module, now in the version catalogue) and `Workload`: every document of a
file, the eight kinds' pods where each keeps them, the pod's host network, PID, IPC and host paths and security
context, each container's image, ports and host ports, probes, mounts and security context (an init container
marked). The fixtures are the real workload YAML in the repositories cloned for the image comparisons
(`fixtures/workloads/README.md`). Reading them taught two things: the external-secrets documentation wraps its
manifests in Liquid's `{% raw %}` for its site, so `{%` marks a template as well as `{{`; and a documentation fragment
with indented `...` where fields are left out is not YAML, so it is unread rather than read in part. None of the
cloned repositories holds a plain workload that asks for privileges, so the security fields are tested on inline YAML;
slice 2's estate will give real ones for the checks.

Process: the Liquid check went in with its test rather than after seeing the test fail.

## Built, slice 2 (9 Nov)

Discovery: `ManifestPaths` picks the candidates; `RepoScan` lists them beside the Dockerfiles; `DiscoveryOutput`
writes `yaml.jsonl` and stores each file once in `yaml/` (the stages after discovery read every file in `blobs/` as
a Dockerfile, so YAML stays out of it); `BlobFetch` takes where to store, so YAML is fetched in the same batches of 50
and its problems recorded as the Dockerfiles' are; the summary counts YAML locations and files fetched; `Combine`
merges `yaml.jsonl` and `yaml/` too. Facts: `ManifestRun`, after the Dockerfiles, writes `workloads.jsonl` (each
workload once with its file's locations) and `yaml-rates.json` (files read, with workloads, workloads, templates,
not YAML).

Measured on the ten repositories cloned for the image comparisons, walked as discovery would: with the first filter,
1,647 candidates of 2,506 YAML files, 16 holding a workload, most of them test inputs (ci-tools' integration
expectations), documentation snippets or examples; asked 9 Nov, tests, docs, examples and samples are left out too:
596 candidates, 6 holding a workload. Of those 6, four are pieces of one Kustomize base (ptp-operator's
`config/default/*_patch.yaml`, `config/manager/env.yaml`): a patch names no image, which slice 3 has to treat as part
of a deployment, not one of its own. Still about one candidate in a hundred holds a workload; a survey on a real
organisation, run by the owner, gives the numbers that count.

## The owner's survey (9 Nov)

`survey --try https://github.com/openshift`, rerun into the first try's folder: 24 repositories newly scanned (the 32
from the first try were finished, so discovery skipped them and listed none of their YAML; a discovery written before
this slice needs a new folder to list its YAML). 4,539 YAML locations, 2,568 files fetched, 21 holding a workload,
1,657 templates. One repository, openshift/openshift-tools, held 2,503 of the files: most a copy of openshift-ansible
under `openshift/installer/vendored/`, its 17 workloads and 1,436 of the templates. `vendored/` is now left out like
`vendor/`; on the same list that leaves 530 files and 4 workloads, the vendored copies' gone and the real ones kept:
openshift-tools' own `sync` DaemonSet (privileged, `runAsUser: 0`, the host's network, PID and paths) and
image-inspector's privileged Pod mounting the Docker socket, both now fixtures for the reader and for slice 3's
checks. The 223 templates left are Ansible roles' Jinja files.

