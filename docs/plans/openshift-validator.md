# From estate summary to action plan: an OpenShift validator

Status: planned 8 Nov; questions answered 8 Nov; tests to write.

## Why

Asked 8 Nov: the estate summary says what the Dockerfiles are; the owner wants it turned into an action plan, what
to change, where, in what order. That needs a validator that knows what the platform the images run on will
refuse or break, since "runs as root" is fatal on one Kubernetes and harmless on another. OpenShift first.

The rules today are platform-neutral house rules (S1-S4, R1-R3, H1-H3, I1, I2), detect-only, each finding sampled
for people to classify. The validator does not replace them: it reads the same facts, lineage and catalogue
(`catalogue.md`) through a **platform profile**, says for each image whether it will start, misbehave or run on
that platform, and turns the findings into actions.

## What OpenShift does to an image

OpenShift 4's default security context constraint, `restricted-v2` (and Pod Security Admission's `restricted`
level), with no special grants:

| OpenShift does | What an image must do | Check (on the effective image where lineage exists, else the Dockerfile) | Today |
| --- | --- | --- | --- |
| runs the container as an arbitrary UID from the namespace's range, group 0 | work as any UID in group 0; never need root | final user root or named; a named user that only resolves through `/etc/passwd` | S4, R3 partly |
| gives that UID no write access to the image's files | make every path written at run time group-0 writable (`chgrp 0`, `chmod g=u`) | `VOLUME`, `WORKDIR`, `HOME`, and directories the image's own steps create, not group-0 writable | S4 partly |
| drops all capabilities and forbids privilege escalation | bind no port below 1024; use no setuid binary, `sudo` or `su` | exposed ports below 1024; `sudo`, `su`, `gosu`, `setcap` in the entrypoint or its scripts | new |
| sends SIGTERM to PID 1 on shutdown | start with an exec-form entrypoint or a signal-forwarding init | shell-form `ENTRYPOINT` or `CMD` | H2 |
| pulls only from registries the cluster allows (image config, mirrors) | come from an allowed registry, pinned | base registry not on the profile's list; unpinned base | S1, R1 |
| ignores `HEALTHCHECK` and does not create `VOLUME`s | declare probes and volumes in the deployment instead | `HEALTHCHECK` or `VOLUME` present (information, an action for the deployment) | new |
| gives no `/etc/passwd` entry for the arbitrary UID | tolerate it (or use `nss_wrapper`) | a start that reads the user's name or home | new, information |

Each check has a severity **on the profile**: *refused* (the pod will not be admitted), *breaks* (admitted but
fails or misbehaves: cannot write, cannot bind, killed unclean), *drift* (works, but reproducibility or policy),
*information* (for the deployment, not the image). Other platforms are other profiles later: upstream Kubernetes
with Pod Security Admission `restricted` refuses a non-numeric or root `USER` outright (`runAsNonRoot` needs a
numeric UID), where OpenShift substitutes its own UID; EKS, AKS and GKE defaults differ again.

## The profiles

Data, not code: a profile file the estate owner can choose, copy and change (the SCC, its UID behaviour, the
capabilities it drops, whether privilege escalation is allowed, seccomp, the allowed registries), read like
`allow.csv` is today. The validator's checks are fixed; the profile decides which apply and how severe each is.
Two ship (asked 8 Nov), every namespace as OpenShift ships it, no grants:

| | `openshift-4-restricted-v2` (default; OpenShift 4.11 on) | `openshift-4-restricted` (v1; before 4.11, and clusters that kept it) |
| --- | --- | --- |
| user | arbitrary UID from the namespace's range, group 0 | the same |
| capabilities | all dropped; a pod may add back only `NET_BIND_SERVICE` | KILL, MKNOD, SETUID, SETGID dropped; the rest of the runtime's defaults kept |
| privilege escalation | forbidden (`no_new_privs`): a setuid binary, `sudo` or `su` cannot raise privilege | allowed, but with SETUID and SETGID dropped `sudo` and `su` still fail to switch user |
| seccomp | `RuntimeDefault` required: a syscall the default profile blocks fails | not required |
| ports below 1024 | not bindable by the arbitrary UID unless the deployment adds `NET_BIND_SERVICE` | not bindable by a non-root UID either (no ambient capability) |

So most checks agree between the two; they differ in severity for privilege escalation and seccomp, and in the
action for a low port (v2: move the port, or add `NET_BIND_SERVICE` in the deployment).

## The action plan

Findings become actions, each naming:

- **what** to change (for example "make `/opt/app` group-0 writable", "move the listener off port 80", "pin
  `ubi9/ubi-minimal` by digest"), with the DSL's fix when one exists;
- **where**: the Dockerfile in the chain that should change (a base fixed once fixes every image built on it, from
  lineage or the catalogue's inferred links), its repositories and paths;
- **reach**: how many images and locations it fixes, through the archetype (one fix to an archetype's
  representative is a template for its copies) and through the images built on the base;
- **severity** on the profile, so *refused* and *breaks* come first.

Actions are ordered by severity, then reach, and shown two ways (answered 8 Nov): by fix (a base or archetype
fixed once, with every image it reaches: for the platform team) and by owning repository (for each team's backlog). Output from a new stage, `validator`, after `rules` (answered 8 Nov): `action-plan.json`
and `action-plan.csv` for the reporting database, and a page in the report, "Action plan for OpenShift", that
leads with how many images would not start or would break, then the actions.

## Questions

1. Which OpenShift is the default: `restricted-v2` with no grants (every namespace as shipped), or should the
   profile describe namespaces that grant `anyuid` or `nonroot-v2`, per team?
2. What the validator knows of deployments: images only (the Dockerfile and lineage), or also the deployment
   manifests in the same repositories (Helm charts, Kustomize, `Deployment` YAML), which say the ports, probes,
   volumes and security context really used?
3. How actions are grouped for people: by base and archetype (fix the template once), by owning repository or team,
   or both?
4. A module of its own (`validator`, between `rules` and `report`), or profiles added to `rules`?
5. Where the catalogue goes (its question 4): the validator reads it, so in `report` as planned, or produced by
   the validator's stage?

Answers (8 Nov): 1, `restricted-v2` with no grants as the default, and `restricted` (v1) as a second profile to
choose; 2, images first (Dockerfiles and lineage), deployment manifests a later slice, as discovery would have to
fetch them; 3, both views, by fix and by repository; 4, a `validator` module between `rules` and `report`; 5, the
catalogue is produced by `report` as planned, and the validator reads facts, lineage and the rules' findings
directly, so it does not wait on the report.

## Tests, in order

One assertion each, several in `assertAll`; each check driven from real Dockerfiles in the fixture bank, with the
OpenShift behaviour it predicts.

1. `Profile`: both shipped profiles read; a copied profile with a field changed; a bad value refused by name.
2. Each check on facts (and on the effective image where lineage has one), one at a time, in the table's order:
   arbitrary UID, group-0 writable paths, low ports, privilege escalation, signals to PID 1, registry and pinning,
   `HEALTHCHECK` and `VOLUME`, the user's name; each with its severity under v2 and under v1.
3. `Verdict`: per image, refused, breaks, drift or runs, from its findings under a profile.
4. `Actions`: findings to actions (what, where in the chain, reach through bases and archetypes, the DSL's fix),
   ordered by severity then reach; the by-repository view of the same actions.
5. `ValidatorRun` writing `action-plan.json` and `action-plan.csv`; `ValidatorMain` reading `PROFILE` (default
   v2) and its other settings.
6. The report's "Action plan for OpenShift" page.
7. A tagged test (`validate`, as `roundTrip` is) over a real estate, its counts committed as a ratchet.
