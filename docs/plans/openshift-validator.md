# From estate summary to action plan: an OpenShift validator

Status: planned 8 Nov; questions answered 8 Nov; built after the catalogue (`catalogue.md`), whose extended facts it
reads.

## Why

Asked 8 Nov: the estate summary says what the Dockerfiles are; the owner wants it turned into an action plan, what
to change, where, in what order. That needs a validator that knows what the platform the images run on will
refuse or break, since "runs as root" breaks an image on one Kubernetes, is refused outright on another, and is
harmless on a third. OpenShift first.

The rules today are platform-neutral house rules (S1-S4, R1-R3, H1-H3, I1, I2), detect-only, each finding sampled
for people to classify. The validator does not replace them: it reads the same facts, lineage and findings through
a **platform profile**, says for each image whether it will run, misbehave or need its deployment changed on that
platform, and turns the findings into actions.

## What OpenShift does to an image

Under OpenShift 4's default security context constraint, `restricted-v2`, with no special grants (OpenShift keeps
each namespace's Pod Security Admission labels in step with it):

| OpenShift does | What an image must do | Check (on the effective image where lineage exists, else the Dockerfile) | Today |
| --- | --- | --- | --- |
| runs the container as an arbitrary UID from the namespace's range, group 0, whatever the image's `USER` | work as any UID in group 0; need neither root nor its own named user | final user root, unset or named | S4 (needs lineage) |
| gives that UID no write access to files it does not own | make every path written at run time group-0 writable (`chgrp 0`, `chmod g=u`) | `VOLUME`, `WORKDIR`, `HOME` and directories the image's own steps create, with no group-0 step over them | S4 partly (any group-0 step anywhere in the chain counts) |
| drops all capabilities | bind no port below 1024 | exposed ports below 1024 | new |
| forbids privilege escalation | use no setuid binary, `sudo`, `su` or `gosu` | those in the entrypoint or command, or `sudo` installed | new |
| sends SIGTERM to PID 1 on shutdown | start with an exec-form entrypoint or a signal-forwarding init | shell-form `ENTRYPOINT` or `CMD` | H2 |
| pulls only from registries the cluster allows (its image configuration and mirrors) | come from an allowed registry, pinned | base registry not on the profile's list; unpinned base | S1, R1 |
| ignores `HEALTHCHECK`; makes each `VOLUME` path a plain directory in the container, not a volume | declare probes, and volumes for data that must outlive the container, in the deployment | `HEALTHCHECK` or `VOLUME` present | new (information) |
| may give the arbitrary UID no `/etc/passwd` entry | tolerate a UID with no name or home | a start that reads the user's name or home (`whoami`, `$USER`, `~`) | new (information) |

Each finding has a severity **on the profile**: *refused* (the pod is not admitted), *breaks* (admitted, but fails
or misbehaves: cannot write, cannot bind, killed unclean), *drift* (runs, but against reproducibility or policy),
*information* (for the deployment rather than the image). On OpenShift an image alone is never *refused*: the SCC
replaces its user rather than rejecting it, so *refused* arrives with the deployment manifests (a pod asking for
more than its SCC allows) and with other platforms' profiles. Upstream Kubernetes with Pod Security Admission
`restricted`, for one, refuses a root or non-numeric `USER` (`runAsNonRoot` needs a numeric UID to verify).

Each finding also says how sure it is: *read* (from the Dockerfile's instructions or the image's configuration) or
*inferred* (a heuristic on text, such as which paths are written at run time, or a port the application might not
use), so the plan can show the read ones first.

## The profiles

Data, not code: a profile file the estate owner chooses, copies and changes (the SCC, its UID behaviour, the
capabilities it drops, whether privilege escalation is allowed, seccomp, the allowed registries), read as
`allow.csv` is today. The validator's checks are fixed; the profile decides which apply and how severe each is.
Two ship (asked 8 Nov), each describing every namespace as OpenShift ships it, with no grants:

| | `openshift-4-restricted-v2` (the default; OpenShift 4.11 on) | `openshift-4-restricted` (v1; before 4.11, and clusters that kept it) |
| --- | --- | --- |
| user | arbitrary UID from the namespace's range, group 0 | the same |
| capabilities | all dropped; a pod may add back only `NET_BIND_SERVICE` | KILL, MKNOD, SETUID and SETGID dropped; the runtime's other defaults kept |
| privilege escalation | forbidden (`no_new_privs`): a setuid binary, `sudo` or `su` cannot raise privilege | allowed, but with SETUID and SETGID dropped `sudo` and `su` still cannot switch user |
| seccomp | `RuntimeDefault` required: a system call the default profile blocks fails | not required |
| ports below 1024 | not bindable by the arbitrary UID: a pod may add `NET_BIND_SERVICE`, but a non-root process gains it only through a file capability, which `no_new_privs` blocks | not bindable by a non-root UID either: it has no capability unless its binary carries a file capability |

Most checks agree between the two; they differ in the severity of privilege escalation and seccomp. For a low port
the action is the same under both: listen on a port above 1024 and let the Service expose the low one.

## The action plan

Findings become actions, each naming:

- **what** to change (for example "make `/opt/app` group-0 writable", "move the listener off port 80", "pin
  `ubi9/ubi-minimal` by digest"), with the DSL's fix when one exists;
- **where**: the Dockerfile in the chain that should change (a base fixed once fixes every image built on it, from
  lineage or the catalogue's inferred links), with its repositories and paths;
- **reach**: how many images and locations it fixes, through the archetype (one fix to an archetype's
  representative is a template for its copies) and through the images built on the base;
- **severity** on the profile, and how sure the finding is.

Actions are ordered by severity, then reach, then certainty, and shown two ways (answered 8 Nov): by fix (a base
or archetype fixed once, with every image it reaches: for the platform team) and by owning repository (for each
team's backlog).

## The stage

A `validator` module between `rules` and `report` (answered 8 Nov). It reads `facts.jsonl` (with the catalogue's
extensions), lineage's `effective.jsonl` and `links.jsonl` when given, the rules' `findings.jsonl`, discovery's
manifest, and the profile. Settings: `FACTS_DIR`, `DISCOVERY_DIR`, `RULES_DIR`, `LINEAGE_DIR` (optional), `PROFILE`
(a shipped profile's name or a file; default `openshift-4-restricted-v2`), `OUT_DIR`. It writes
`action-plan.json` and `action-plan.csv` (with their DDL) for the reporting database; the report gains a page,
"Action plan for OpenShift", that leads with how many images would break or drift under the profile, then the
actions by fix, then by repository.

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
choose (asked after: options for both); 2, images first (Dockerfiles and lineage), deployment manifests a later
slice, as discovery would have to fetch them; 3, both views, by fix and by repository; 4, a `validator` module
between `rules` and `report`; 5, the catalogue is produced by `report`, and the validator reads facts, lineage and
the rules' findings directly, so it does not wait on the report.

## Tests, in order

One assertion each, several in `assertAll`; each check driven from real Dockerfiles in the fixture bank, with the
OpenShift behaviour it predicts.

1. `Profile`: both shipped profiles read; a copied profile with a field changed; a bad value refused by name.
2. Each check, one at a time in the table's order, on a Dockerfile's facts and on an effective image where lineage
   has one: the user, group-0 writable paths, low ports, privilege escalation, signals to PID 1, registry and
   pinning, `HEALTHCHECK` and `VOLUME`, the user's name; each with its severity under v2 and under v1, and whether
   it is read or inferred.
3. `Verdict`: per image, breaks, drift, information or runs, from its findings under a profile.
4. `Actions`: findings to actions (what, where in the chain, reach through bases and archetypes, the DSL's fix),
   ordered by severity, reach and certainty; the by-repository view of the same actions.
5. `ValidatorRun` writing `action-plan.json` and `action-plan.csv`; `ValidatorMain` reading its settings, `PROFILE`
   defaulting to v2, a bad value refused by name.
6. The report's "Action plan for OpenShift" page.
7. A tagged test (`validate`, run by `./gradlew validate`, as `roundTrip` is) over a real estate, its counts
   committed as a ratchet.
