# From estate summary to action plan: an OpenShift validator

Status: planned 8 Nov; questions answered 8 Nov; slice 1 (the module, the profiles, the image checks, verdicts, actions and the run) built 9 Nov.

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

## Beyond the platform: antipatterns (asked 8 Nov: all of them)

Besides what OpenShift refuses or breaks, the validator reports the estate's antipatterns, each with its action and
reach as above; those the rules already find keep their IDs. Severity is *breaks*, *drift* or *information* as the
platform's checks have it; each is confirmed on a sample of its findings before its fix is offered, as every rule is.

| Group | Antipattern | Data | Today |
| --- | --- | --- | --- |
| hierarchy | a stale intermediate: its base published since it was built, so its fix reaches nothing below it | lineage (`outdated-bases.jsonl`) | new |
| hierarchy | a deep chain: many estate images between a leaf and its root | catalogue hierarchy | new |
| hierarchy | an application image used as another's base | catalogue hierarchy (a parent with a start command and children) | new |
| hierarchy | siblings repeating the same first steps: a shared base to extract | facts steps, compared across a parent's children | new |
| hierarchy | a floating base (`latest`, or no tag) | facts | R1 |
| hierarchy | many images on different digests of one base | facts and lineage | new |
| hierarchy | a published intermediate with no children; children of an image no estate Dockerfile builds | lineage | new |
| footprint | could be smaller: a final stage on a full base that installs nothing (micro or distroless), or that installs packages (minimal) | catalogue footprint and packages | new |
| layers | a single-stage build that ships its toolchain (compilers, `-devel`, `git`, `curl`) | facts packages, stage count | new |
| layers | a file removed in a later layer than the one that added it | facts steps and shell commands | new |
| layers | `chown -R` or `chmod -R` in a `RUN` of its own after a `COPY` | facts per-stage permissions | new |
| layers | `COPY --from=stage / /`: a whole stage copied | facts copies | new |
| layers | `apt-get upgrade`, `dnf update` in the build | facts packages | new |
| layers | `ADD` of a URL without `--checksum`, or of a plain local file | facts copies | new |
| layers | an install not cleaned in its `RUN` | facts | H1 |
| layers | `curl` or `wget` piped to a shell | facts | S3 |
| layers | packages without a version | facts | I1 |
| layers | adjacent `RUN`s that could be one | facts | H3 |
| permissions | final user root or unset | facts and lineage | S4 |
| permissions | `chmod 777` or `a+w` | facts per-stage permissions | new |
| permissions | paths written at run time not group-0 writable | facts per-stage permissions | S4 partly |
| permissions | `sudo` installed, setuid relied on | facts packages and commands | new (platform check above) |
| permissions | a secret in `ENV` or `ARG` | facts (hidden) | S2 |
| permissions | a key or credential copied in, then removed | facts copies and commands | new |
| start | a shell-form entrypoint | facts | H2 |
| start | no `ENTRYPOINT` or `CMD` in a runnable image | facts, catalogue | new |
| start | an exposed port below 1024 | facts | new (platform check above) |
| start | a path changed after a `VOLUME` declared it (the change is discarded at build) | facts steps and per-stage permissions | new |
| start | `ENV` holding build-only values (better an `ARG`) | facts config and steps | new, information |

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
`action-plan.json`; the report gains a page,
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
5. `ValidatorRun` writing `action-plan.json`; `ValidatorMain` reading its settings, `PROFILE`
   defaulting to v2, a bad value refused by name.
6. The report's "Action plan for OpenShift" page.
7. A tagged test (`validate`, run by `./gradlew validate`, as `roundTrip` is) over a real estate, its counts
   committed as a ratchet.

## Slice 1: the module, the profiles and the image checks (9 Nov)

- **A profile is a JSON file**, as every single object the pipeline reads or writes is: its `name`, a sentence
  saying what it `describes`, the `allowedRegistries` (empty: any) and a `severity` per check (`breaks`, `drift`,
  `information` or `off`). How OpenShift behaves under it (the arbitrary UID, the capabilities, privilege
  escalation, seccomp) is what sets those severities, so it is written once, in the shipped profiles and the table
  below, rather than as fields no check reads. `refused` waits for the deployment manifests.
- **The checks**, numbered in the order of the table above, on the effective image where lineage has one and
  otherwise the Dockerfile's facts:

| ID | Check | Certainty | v2 | v1 |
| --- | --- | --- | --- | --- |
| P1 | final user root, unset or named | read | breaks | breaks |
| P2 | a path written at run time (`VOLUME`, `WORKDIR`, a directory its steps create) with no group-0 step over it | inferred | breaks | breaks |
| P3 | an exposed port below 1024 | read | breaks | breaks |
| P4 | `sudo`, `su` or `gosu` in the start command, or `sudo` installed | read | breaks | breaks |
| P5 | a setuid or setgid bit set (`chmod u+s`, `g+s`, `4755`) | read | breaks | information |
| P6 | a shell-form `ENTRYPOINT` or `CMD` | read | breaks | breaks |
| P7 | a base from a registry not on the profile's list | read | breaks | breaks |
| P8 | a base not pinned by digest | read | drift | drift |
| P9 | `HEALTHCHECK` or `VOLUME` | read | information | information |
| P10 | a start command reading the user's name or home (`whoami`, `$USER`, `$HOME`, `~`) | inferred | information | information |

  Under v1, `sudo`, `su` and `gosu` still fail (SETUID and SETGID are dropped), but another setuid binary still
  gains what the bounding set allows, so P5 alone differs.

Tests, in order: `Profile` (both shipped profiles read; a copied one with a severity changed; an unknown check, a bad
severity or a missing field refused by name); then P1 to P10 one at a time, each on real lines from the fixture bank;
then the verdict, the actions, the run and the page as above.

Found on the way:

- The outputs follow the pipeline's rule for collections: `actions.jsonl` (one action per line, most urgent first)
  and `verdicts.jsonl` beside the single summary object `action-plan.json`, which counts the actions per
  repository; the by-repository view is the same actions read through each one's locations.
- A finding's certainty can depend on the image: P1 on a user the Dockerfile never sets is inferred, since the base
  may set one. The effective image from lineage turns those into read findings; that is the next slice.
- Metadata-only bundles (`FROM scratch`, no start command) never run as containers; they are counted apart rather
  than judged (676 of the combined estate's P1 findings were bundles).
- RULES_DIR and LINEAGE_DIR wait for the slices that read them (the antipatterns, the effective image).

Measured (9 Nov, stored facts with the catalogue's fields), each in about a second:

| | judged | not runnable | breaks | drift | information | runs | actions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OKD | 2,440 | 139 | 1,992 | 357 | 2 | 89 | 4,610 |
| combined estate | 4,472 | 685 | 3,641 | 642 | 2 | 187 | 8,864 |

The two profiles give the same verdicts on both estates: P5 (the only difference) finds three images, each breaking
on another check too. By check on the combined estate: P1 2,982 (most inferred), P2 1,142, P3 58, P4 19, P5 3, P6
369, P8 3,984, P9 303, P10 4. A sample of five per check read right, except P2 on sclorg's images, whose
`fix-permissions` script does the group-0 work out of the facts' sight.

The report's page (test 6) built 9 Nov: with `VALIDATOR_DIR`, `openshift.html` leads with the verdicts under the
profile, lists the first 250 actions most urgent first, each linked to its Dockerfile's page, then the actions per
repository; the summary leads with the same sentence. On OKD: 40,037 links, none broken.

P2 reads sclorg's `fix-permissions <path>` in the final image's `RUN` steps as a recursive group-0 write (9 Nov), and
leaves a `HOME` in `/tmp` alone: P2 on the combined estate 1,142 -> 1,055, on OKD 366 -> 360; verdicts on the
combined estate 3,590 break, 693 drift, 2 information, 187 run.

Test 7 built 9 Nov: `./gradlew validate` (tag `validate`) runs the validator over the combined estate's stored facts
and compares the images judged, the verdicts and the actions per check exactly with
`validator/src/test/resources/estate/expected.json`, so a change to a check carries its effect in the diff.

P2 expands `$VAR` and `${VAR}` from the image's final environment in the paths it reads (`VOLUME`, `WORKDIR`, `HOME`,
`chmod` and `COPY --chmod` targets, `fix-permissions` arguments), found 9 Nov sampling the fixes
(`docs/plans/validator-reach.md`): sclorg's minimal images run `fix-permissions ${APP_ROOT}`. On the combined estate
P2 1,055 -> 1,133: 50 findings gone (a sample of six each made the path group-writable through a variable), 128 new
(paths written with a variable, skipped before, now judged as if written out, such as tomcat's `WORKDIR
$CATALINA_HOME`), 78 reworded. Verdicts 3,562 break, 723 drift, 2 information, 185 run. On OKD P2 stays 360 (12 gone,
12 new).

