# From estate summary to action plan: an OpenShift validator

Status: planned 8 Nov; questions open.

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

## The profile

Data, not code: `openshift-4-restricted-v2` as the default, a file the estate owner can copy and change (the SCC,
its UID behaviour, capabilities, the allowed registries, whether ports below 1024 are bindable), read like
`allow.csv` is today. The validator's checks are fixed; the profile decides which apply and how severe each is.

## The action plan

Findings become actions, each naming:

- **what** to change (for example "make `/opt/app` group-0 writable", "move the listener off port 80", "pin
  `ubi9/ubi-minimal` by digest"), with the DSL's fix when one exists;
- **where**: the Dockerfile in the chain that should change (a base fixed once fixes every image built on it, from
  lineage or the catalogue's inferred links), its repositories and paths;
- **reach**: how many images and locations it fixes, through the archetype (one fix to an archetype's
  representative is a template for its copies) and through the images built on the base;
- **severity** on the profile, so *refused* and *breaks* come first.

Actions are grouped and ordered by severity, then reach. Output from a new stage after `rules`: `action-plan.json`
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

## Tests, in order

To be written once the questions are answered; the checks are test-driven from real Dockerfiles in the fixture
bank, each with the OpenShift behaviour it predicts.
