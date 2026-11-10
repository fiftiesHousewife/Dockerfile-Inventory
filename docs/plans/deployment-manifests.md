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

## Slice 3b: linking, answers, actions (planned and built 10 Nov)

Design:

- A container's `image` is read as a reference (`ImageReference`): the registry host dropped (a first segment with
  `.` or `:`, or `localhost`), then the path, the tag and the digest. It matches an image lineage read
  (`images.jsonl`) when its path is the image's, or the Artifactory repository's name and then the image's, and its tag
  is the image's (`latest` when it names none). Lineage's credited links (`links.jsonl`) give the config digest's
  Dockerfiles (`ImageLinks`).
- Each container in `workload-verdicts.jsonl` names its Dockerfiles and whether its tag floats, or why it is unlinked:
  no lineage, no image matching, no credited link, a reference by digest alone (lineage keeps config digests, not
  manifest digests), or no image named (a Kustomize patch).
- A floating reference is a thing to fix and fails: `latest` or no tag, without a digest. D7 (`FloatingImage`) for a
  container, P11 (`FloatingBase`) for a `FROM`, both breaks in the shipped profiles; P8 keeps a fixed tag without a
  digest, at drift.
- Linked, the workload's verdict is the worst of its pod findings and its images' platform findings, with P9
  recomputed for the container: its liveness or readiness probe answers a `HEALTHCHECK`, a mount at a `VOLUME` path
  answers that volume (`DeploymentHints.unanswered`).
- The pod findings are actions and fixes, each naming the YAML (its blob SHA and locations). The images' findings
  stay actions on their Dockerfiles as before.

Tests, in order: `ImageReference` (host, path, tag, digest; floating); P11 and P8 split, and a profile without P11 or
D7 refused; `FloatingImage`; `ImageLinks` (path and tag whatever the host, the repository's name first, latest for no
tag, an uncredited link, no image, a digest alone, no image named, no lineage); `DeploymentHints.unanswered`;
`WorkloadVerdict` with its containers and its images' findings; `ValidatorRun` writing the pod findings as actions and
linking through a lineage fixture; `validate` updated with the measured move from P8 to P11.

Questions:

1. How a reference matches an image. Manifests name `registry/path:tag` or `registry/path@sha256:...`. Lineage keeps
   each Artifactory repository's image path and tag and the config digest (not the manifest digest a `@sha256`
   reference names). Match on the image path and tag alone, whatever registry host the manifest names, or only when the
   host is the Artifactory being read?
   Answered 10 Nov: on the image path and tag alone, whatever the host; manifests often name a mirror or an alias
   of the same registry.
2. A floating tag (`latest`, or none). Link it to the image that tag points at when lineage read it, or leave it
   unlinked since the cluster may pull another?
   Answered 10 Nov: link it to the image the tag pointed at when lineage read it, and mark the link floating in
   `workload-verdicts.jsonl`.
3. The pod findings as actions. Into `actions.jsonl` and `fixes.jsonl` beside the images' (each naming the YAML file
   to change, and for an image finding the manifest answers, the YAML instead of the Dockerfile), or kept in
   `workload-verdicts.jsonl` until the report's slice shows them?
   Answered 10 Nov: into `actions.jsonl` and `fixes.jsonl`, each naming the YAML file to change.
4. A floating reference is itself a thing to fix and fails the validator (owner, 10 Nov): a new pod check, D7, for a
   container naming a floating tag. A `FROM` of `latest` or no tag fails too (P11); a fixed tag without a digest stays
   drift (P8). Combined estate before: 3,987 P8 actions, 551 `latest` and 621 untagged.

## Built, slice 3b (10 Nov)

`ImageReference` reads a container's image (`ImageReference.host` also serves P7's `BaseRegistry`); `ImageLinks`
reads lineage's `images.jsonl` and credited `links.jsonl` into a `ContainerLink` per container; `ContainerImage`
gives a linked image's platform findings with P9 from `DeploymentHints.unanswered`; `WorkloadVerdict` carries the
containers and both kinds of finding; `ValidatorRun` adds the pod findings to the actions, with the workloads'
locations; `ActionPlan` holds the plan's counts. `FloatingImage` (D7) and `FloatingBase` (P11) are breaks in both
profiles; `UnpinnedBase` (P8) leaves floating bases to P11.

Measured: on the combined estate (`./gradlew validate`, updated in the same commit) P8's 3,984 actions became 2,812 P8
and 1,172 P11, and 238 images moved from drift to breaks; nothing else changed. On the owner's survey of
`github.com/openshift` (`out/openshift-try`, no lineage): 21 workloads, 15 refused, 2 break (D7: `rhel7` and
image-inspector's `:latest`), 4 run; 41 pod actions; 12 containers unlinked for want of lineage, 10 naming no image
(Kustomize patches and templates). Linking is tested on a lineage fixture made by hand; a survey with both lineage and
workloads will measure it.

## Slice 4: the report (planned and built 10 Nov)

Why: the plan holds the workloads' verdicts and actions, but the OpenShift page shows neither, and its lead counts
images only (no `refused`).

Design:

- `ActionPlanView` reads `workload-verdicts.jsonl` when the validator wrote one, and the plan's `workloads` and
  `workloadVerdicts`.
- The lead adds a sentence when there are workloads: how many would be refused admission, break, drift and run.
- The OpenShift page gains a "Workloads, worst first" table (`WorkloadTable`): the verdict; kind, name and namespace;
  the YAML's first location; each container with its image, the Dockerfiles it runs (linked to their pages when
  catalogued, flagged when the tag floats) or why it is unlinked; the findings. At most `ActionPlanPage.SHOWN` rows.
- The fixes table's column becomes "File to change": a pod fix names its YAML.
- Without workloads, every page is as before.

Tests, in order: `ActionPlanView` reading a validator folder with workloads (and one without); the lead's workload
sentence; `WorkloadTable`'s rows (a linked container, an unlinked one, a floating one, the order); the page with and
without workloads; the report run on the fixture writing the section.

Questions: none. The slices above already put workloads on the OpenShift page; a Dockerfile's page listing the
workloads that run it is left for later.

## Built, slice 4 (10 Nov)

`ActionPlanView` reads `workload-verdicts.jsonl` (`PlannedWorkload`, `PlannedContainer`, `PlannedFinding`) and the
plan's workload counts; `ActionPlanPage.workloads` writes the lead's sentence; `WorkloadTable` the table, worst first
then by name. Fixture: the validator's output on the owner's survey, trimmed (`report/src/test/resources/fixtures/
validator-workloads`). On that survey the summary reads "Of 21 workloads, 15 would be refused admission, 2 would break
and 4 run as they are", and the OpenShift page lists the 21. The fixes table's column is "File to change"; otherwise
the pages without workloads are as before.


## Slice 5: a Dockerfile's workloads (planned and built 10 Nov)

Why: the OpenShift page says which Dockerfiles a workload runs; the Dockerfile's own page should say the reverse.

Design: `Deployments.byDockerfile` gathers, for each Dockerfile, the workloads whose containers lineage links to it;
`Deployments.section` writes a "Runs in" table on its page (the workload's verdict; kind, name and namespace; its
YAML's first location; the containers running it, with their images and whether the tag floats) and links to the
OpenShift page. A Dockerfile no workload runs, and every page without the validator's workloads, is as before.

Tests, in order: `byDockerfile` (a workload once per Dockerfile it runs, two containers on the same Dockerfile once,
unlinked containers nowhere); `section` (its row, empty for none); `Pages` putting it on the linked Dockerfile's page
only.

Questions: none (asked 10 Nov: "go ahead with the dockerfile page").

Built 10 Nov: `Deployments` (`byDockerfile`, `section`, `running`), with `WorkloadTable.described` shared by both
tables; `Pages` adds the section to each Dockerfile's page. On the owner's survey (no lineage, so no container
linked) every page is byte for byte as before; the section waits for a survey with lineage to show on real data.

