# Deployment manifests in the validator: plan

Status: planned 9 Nov; the questions answered 9 Nov; slices 1 and 2 built 9 Nov; slice 3a built 10 Nov.

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


## Slice 3a: refused, the pod checks, a verdict per workload (planned 10 Nov)

Slice 3 is split: 3a judges each workload on its own pod settings; 3b links its containers to Dockerfiles through
lineage and lets the manifest answer its image's P9.

Design:

- `Severity` and `Verdict` gain `REFUSED`, above `BREAKS`. No image check is refused in either shipped profile, so
  the images' verdicts and the estate ratchet stay as they are.
- `Check` gains D1 to D6, pod checks, beside the platform checks (P) and the rules. A profile must give every one a
  severity, under `pod` (as the rules have `rules`), and may list `allowedCapabilities`.
- `restricted-v2`: D1 to D6 refused; `NET_BIND_SERVICE` the one capability allowed. `restricted` (v1): D1, D2, D3
  and D5 refused, no capability allowed; D4 and D6 off, since v1 allows privilege escalation and sets no seccomp
  profile.
- The checks read each container with its pod's settings under it (a container's `runAsUser` or `seccomp` overrides
  the pod's), init containers included:
  - D1, a privileged container;
  - D2, a fixed `runAsUser`: 0 is read as refused; any other UID is inferred, admitted only inside the namespace's
    range;
  - D3, an added capability the profile does not allow;
  - D4, `allowPrivilegeEscalation: true`;
  - D5, the host's network, PID or IPC namespace, a host path or a host port;
  - D6, an `Unconfined` seccomp profile.
- A problem carries its own certainty (D2 has both kinds).
- The validator reads `workloads.jsonl` from the facts folder when it is there. It writes `workload-verdicts.jsonl`,
  one line per workload: the YAML's blob SHA, kind, name, namespace, locations, verdict and findings. When there are
  workloads, `action-plan.json` gains `workloads` and `workloadVerdicts`. Without the file, every output stays byte
  for byte what it was.
- Decided without asking (say if wrong): a Kustomize patch, which names no image, is judged as it stands, and 3b
  leaves it unlinked. Pod findings do not become actions until 3b, when they can name the Dockerfile as well as the
  YAML.

Tests, in order:

1. `Verdict.of` a refused finding is `REFUSED`, worse than `BREAKS`.
2. `Profile.parse` reads `refused`, refuses a profile with no severity for a D check (naming it), and reads
   `allowedCapabilities`.
3. The shipped profiles: v2 refuses D1 to D6 and allows `NET_BIND_SERVICE`; v1 turns D4 and D6 off and allows none.
4. `Workload.read` reads the real lines of the owner's survey (`fixtures/workloads/workloads.jsonl`).
5. The checks, one class each, on the real lines (openshift-tools' `sync`, image-inspector, a plain Deployment) and
   inline cases for what the estate lacks: D1; D2 for 0, for 1001, and a container overriding its pod; D3 under each
   profile; D4; D5 naming each host access; D6 at pod and container level, the container's setting winning.
6. `PodValidation.findings` skips checks that are off and gives each problem its certainty.
7. `ValidatorRun` with `workloads.jsonl` writes `workload-verdicts.jsonl` and the counts; without it, the outputs
   are unchanged.

## Built, slice 3a (10 Nov)

`Severity` and `Verdict` have `REFUSED`; `Check` has D1 to D6 (`pod()` beside `platform()` and `rule()`); both
profiles carry `pod` and `allowedCapabilities`, and a profile without a severity for a pod check is refused naming it.
The validator reads `workloads.jsonl` into `Workload` (its own record of facts' row: the validator depends on no other
module), runs `PodValidation`'s six checks (`Privileged`, `FixedUser`, `AddedCapability`, `PrivilegeEscalation`,
`HostAccess`, `Unconfined`, with `ContainerSettings` giving a container its pod's `runAsUser` and seccomp profile when
it sets none), and writes `workload-verdicts.jsonl` (`WorkloadVerdict`). The pod checks are never inherited through a
base (`Inheritance`), and each says what to change, for the actions 3b will make. Without `workloads.jsonl` every
output is as before: `./gradlew validate` holds the combined estate's counts unchanged.

Measured on the owner's survey of `github.com/openshift` (`out/openshift-try`, whose facts still hold the vendored
copies): 21 workloads, 15 refused under `restricted-v2`, 6 run. The refused are the node agents (`sync`, `ovs`, `sdn`,
`proxy-and-dns`: privileged, UID 0, the host's network, PID and paths) and the static master pods (privileged, host
network and paths), which on a real cluster run under the privileged SCC; and image-inspector's privileged Pod mounting
the Docker socket. Fixtures: three of those lines (`validator/src/test/resources/fixtures/workloads.jsonl`).

Next, 3b: link each container's image to a Dockerfile through lineage, let a manifest answer its image's P9, and make
the pod findings actions naming the YAML (and the Dockerfile when linked).

## Slice 3b: linking, answers, actions (planned 10 Nov; questions open)

Design so far:

- A container's `image` is matched to an image lineage read (`images.jsonl`: repository, image, tag, config digest),
  and lineage's credited link (`links.jsonl`) names the Dockerfile's blob SHA. Linked, the workload's verdict is the
  worst of its pod findings and its image's findings, less those the manifest answers.
- The manifest answers P9 for its container when it declares a liveness or readiness probe (for a `HEALTHCHECK`) and
  mounts a volume at each of the image's `VOLUME` paths.
- `workload-verdicts.jsonl` names each container's Dockerfile, or says it is unlinked and why (no lineage, no image
  matching, a Kustomize patch naming no image).

Tests, in order, once the questions are answered: matching a reference (each form the answers allow); a container
linked through a credited link, and one whose link is not credited; P9 answered by probes and mounts, and not when a
`VOLUME` path is left unmounted; a linked workload's verdict taking its image's findings; the pod findings as actions
(per question 3); `ValidatorRun` with lineage and workloads; `validate` unchanged.

Questions:

1. How a reference matches an image. Manifests name `registry/path:tag` or `registry/path@sha256:...`. Lineage keeps
   each Artifactory repository's image path and tag and the config digest (not the manifest digest a `@sha256`
   reference names). Match on the image path and tag alone, whatever registry host the manifest names, or only when the
   host is the Artifactory being read?
2. A floating tag (`latest`, or none). Link it to the image that tag points at when lineage read it, or leave it
   unlinked since the cluster may pull another?
3. The pod findings as actions. Into `actions.jsonl` and `fixes.jsonl` beside the images' (each naming the YAML file
   to change, and for an image finding the manifest answers, the YAML instead of the Dockerfile), or kept in
   `workload-verdicts.jsonl` until the report's slice shows them?
