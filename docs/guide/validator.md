# The OpenShift validator

```bash
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export PROFILE=openshift-4-restricted-v2      # optional: a shipped profile's name or a profile file
export LINEAGE_DIR=/data/lineage              # optional: the users images run as; containers to Dockerfiles
export CATALOGUE_DIR=/data/catalogue          # optional: the catalogue stage's OUT_DIR, for fixes and their reach
export RULES_DIR=/data/rules                  # optional: the rules stage's OUT_DIR, whose findings become actions
export OUT_DIR=/data/validator
docker-inventory validator
```

The validator reads each Dockerfile's facts as the platform in `PROFILE` would run its image, and says what will
break, what drifts and what the deployment must supply. Two profiles ship, each describing every namespace as
OpenShift ships it, with no grants: `openshift-4-restricted-v2` (the default; OpenShift 4.11 and later) and
`openshift-4-restricted` (before 4.11, and clusters that kept it). A profile is a JSON file: copy one from
`validator/src/main/resources/profiles/`, change a check's severity (`breaks`, `drift`, `information` or `off`) or list
the registries the cluster pulls from in `allowedRegistries` (empty: any), set a rule's severity in `rules`, a pod
check's in `pod` (`refused` too), the capabilities a pod may add in `allowedCapabilities`, and pass its path as
`PROFILE`.

With `RULES_DIR`, every rule finding becomes an action too, grouped into fixes like the checks', with the severity the
profile's `rules` gives its rule ID (information when left out; both shipped profiles: S1, S2, S3, S5, S6, S7 and R2
drift; R3, R4, H1, H3 to H9, I1 and I2 information; S4, H2 and R1 off, as P1, P6 and P8 find the same). Rules add
actions, not verdicts: the verdicts say how each image fares on the platform.

| Check | What it finds | Under v2 | Under v1 |
| --- | --- | --- | --- |
| P1 | a final user that is root, unset (inferred: it may come from the base) or named; OpenShift runs an arbitrary UID in group 0. With `LINEAGE_DIR`, a Dockerfile lineage credits images to is judged on the user those images run as (read), naming the image or base that set it when lineage knows; a numeric non-root user there clears it, and when its images differ the worst counts | breaks | breaks |
| P2 | a `VOLUME`, `WORKDIR` or `HOME` never made writable by group 0 in the final stage, by `chmod`, `COPY --chmod` or sclorg's `fix-permissions` (inferred: another script may do it); variables in those paths are expanded from the image's environment | breaks | breaks |
| P3 | an exposed port below 1024 | breaks | breaks |
| P4 | `sudo`, `su`, `gosu` or `su-exec` in the start command, or `sudo` installed in the final image | breaks | breaks |
| P5 | a setuid bit set in the final stage | breaks | information |
| P6 | a shell-form `ENTRYPOINT` (or `CMD` without one): the process never receives SIGTERM | breaks | breaks |
| P7 | a base from a registry off the profile's list | breaks | breaks |
| P8 | a base with a fixed tag but no digest | drift | drift |
| P9 | a `HEALTHCHECK` (ignored) or a `VOLUME` (a plain directory): the deployment must declare probes and volumes | information | information |
| P10 | a start command reading the user's name or home (inferred) | information | information |
| P11 | a floating base: `latest` or no tag, and no digest | breaks | breaks |

Metadata-only bundles (`FROM scratch` with no start command) never run as containers and are counted apart.

When the facts folder holds `workloads.jsonl` (discovery fetched YAML and facts read workloads from it), each workload
is judged on its own pod settings, each container with its pod's settings under it, init containers included; a pod
asking for more than its SCC allows is not admitted:

| Check | What it finds | Under v2 | Under v1 |
| --- | --- | --- | --- |
| D1 | a privileged container | refused | refused |
| D2 | a fixed `runAsUser`: 0 read; any other UID inferred, admitted only inside the namespace's range | refused | refused |
| D3 | an added capability the profile does not allow (v2 allows `NET_BIND_SERVICE`, v1 none) | refused | refused |
| D4 | `allowPrivilegeEscalation: true` | refused | off |
| D5 | the host's network, PID or IPC namespace, a host path or a host port | refused | refused |
| D6 | an `Unconfined` seccomp profile | refused | off |
| D7 | a container naming a floating image: `latest` or no tag, and no digest | breaks | breaks |

With `LINEAGE_DIR`, each container's image is matched to a registry image lineage read, on its path and tag whatever
registry host it names (`latest` when it names none; the Artifactory repository's name may come first), and through
lineage's credited links to its Dockerfiles. A linked workload also takes those images' platform findings, with P9 as
the container leaves it: a liveness or readiness probe answers a `HEALTHCHECK`, a mount at a `VOLUME` path answers it.
The pod findings are actions and fixes, naming the YAML's blob SHA and locations.

| File | Contents |
| --- | --- |
| `action-plan.json` | The profile, how many images were judged and how many not, the verdicts (`breaks`, `drift`, `information`, `runs`: each image's worst finding), the number of actions and of fixes, and the actions per repository |
| `actions.jsonl` | One action per line, most urgent first (severity, then the locations it reaches, then read before inferred): the check, its severity and certainty, what to change, the Dockerfile's blob SHA, the findings behind it and every location |
| `fixes.jsonl` | The actions as fixes, most urgent first (severity, then the locations reached): with `CATALOGUE_DIR`, the same check on an archetype's copies is one fix to its `representative`, listing its `copies`, the Dockerfiles built on them that keep what the fix changes (`inheriting`: for P1 those setting no `USER`; P4, P6 and P10 those setting no start command; P2 those doing no group-0 permission work of their own; P3, P5 and P9 all; P7, P8, P11 and the pod checks none) and every location they reach; without it, one fix per action |
| `verdicts.jsonl` | Each judged Dockerfile's verdict |
| `workload-verdicts.jsonl` | With workloads: each workload's YAML blob SHA, kind, name, namespace, locations, verdict (`REFUSED` above `BREAKS`), its `containers` (each with its `image`, the `dockerfiles` it links to and whether it is `floating`, or why it is `unlinked`) and its findings (the pod's, then its linked images'); `action-plan.json` then counts the `workloads` and their `workloadVerdicts` |

The report shows the plan as a page when given the validator's `OUT_DIR` as `VALIDATOR_DIR`.

`./gradlew validate` runs the validator over a stored estate and compares its counts with those committed in
`validator/src/test/resources/estate/expected.json` (outside `check`; it skips without the estate).
