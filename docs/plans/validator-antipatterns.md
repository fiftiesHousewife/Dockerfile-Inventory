# The validator's antipatterns: plan

Status: planned 9 Nov; the questions answered 9 Nov; slice 1 (the rules' findings in the plan) built 9 Nov.

## Why

Asked 8 Nov (all of them): besides what OpenShift refuses or breaks, the action plan reports the estate's
antipatterns, each as an action with its reach, those the rules already find keeping their IDs; each confirmed on a
sample of its findings before its fix is offered. The table is in [openshift-validator](openshift-validator.md):
about thirty, eleven found today by the rules (S2, S3, S4, R1, H1, H2, H3, I1 and the platform checks), the rest new.

## What exists

- `rules` (stage 3): one class per rule (`Rule`, `EffectiveRule`), `findings.jsonl` with the rule, a severity
  (`HIGH`, `MEDIUM`, `LOW`), the blob SHA and a message (secrets as a hash), `rates.json`, and `samples/`: findings
  drawn per rule for the false-positive measurement.
- `validator` (stage 3c): platform checks P1 to P10, severities from the profile, actions and fixes with reach.

## Design

- The new antipatterns are rules in `rules` (asked 9 Nov), each with its ID, samples and rates, measured on 50
  sampled findings before its fix is offered. The validator reads the rules' `findings.jsonl` (`RULES_DIR`) and turns
  every finding into an action, grouped into fixes with their reach like the platform checks' (copies of an
  archetype; never through the Dockerfiles built on one, since a rule reads one Dockerfile).
- A rule finding's severity comes from the profile (asked 9 Nov): a `rules` map from rule ID to `breaks`, `drift`,
  `information` or `off`; a rule missing from it is information. Both shipped profiles: S1, S2, S3 and R2 drift; R3,
  H1, H3, I1 and I2 information; S4, H2 and R1 off, since P1, P6 and P8 find the same.
- `Check` holds the rules' IDs beside P1 to P10, marked as rules; each has its action's wording in `Actions.WHAT`.
  An ID in `findings.jsonl` the validator does not know is refused by name, so a new rule is added here too.
- Verdicts stay the platform's: how each image fares under the profile. The rules' findings add actions and fixes,
  not verdicts (decided by default; the page's lead is about OpenShift).

## Slices

1. The rules' findings in the plan: `Check` and the profile's `rules`; `RuleFindings.read`; the run and `RULES_DIR`;
   measured on the combined estate.
2. Then the new antipatterns, one rule at a time in `rules`, in the table's order within each group, cheapest data
   first: the layers and permissions groups (facts alone), then start, footprint, and the hierarchy (catalogue and
   lineage).

## Tests, slice 1

1. `Check.rule()`; `Profile.parse` reads `rules`, a missing one is information, an unknown ID or word refused by name;
   the shipped profiles' defaults.
2. `RuleFindings.read`: a finding becomes a read finding with the profile's severity; `off` dropped; an unknown rule
   refused by name.
3. `Actions.WHAT` covers every check; `Inheritance` never reaches through a rule.
4. `ValidatorRun` with a rules directory: the rules' actions and fixes written, verdicts unchanged; `ValidatorMain`
   reads `RULES_DIR`.
5. Measured on the combined estate with the rules rerun.

## Questions answered

1. The new antipatterns live in `rules`; the validator reads their findings (9 Nov).
2. A rule's severity comes from the profile's `rules` map, information when missing (9 Nov).

## Measured, slice 1 (9 Nov)

The rules rerun on the combined estate's stored facts, then the validator with `CATALOGUE_DIR` and `RULES_DIR`:
18,544 actions (8,855 the platform's), 9,091 fixes (4,399), verdicts unchanged (3,562 break, 723 drift, 2
information, 185 run). The rules' actions: R3 5,157, I1 2,356, H3 1,710, H1 287, I2 90, S3 49, S2 40; S4, H2 and R1
off. The first rule fix ranks 2,441st, after every platform fix that breaks: S2 (drift) on six locations.

Found on the way: the ranking's `parse-failures` and `templates` groups are not archetypes (their files are not
alike), but the fixes grouped them as if they were, folding 90 unparseable files into 2 fixes; `Catalogue` now leaves
them out.

## S5: world-writable paths (9 Nov)

`WorldWritable` (rules, medium; drift in both shipped profiles): a `chmod` in the final image (the last stage and
the stages it is built on) whose mode gives others write: the octal mode's last digit has the write bit, or a clause
for `o` or `a` adds `w`. The permission changes are facts' reading of each `RUN` (`StagePermissions.ofRun`, public
for this).

Measured on the combined estate: 468 changes in 381 Dockerfiles (`1777` 243, `a+rwx` 67, `777` 62, `og+rwx` 32,
`a+rwX` 23, the rest under 15 each). The 50-finding sample (`samples/S5.csv`, seed as every rule's) read by hand: all
50 make what they name writable by everyone, so no false positives. Half of all findings are the sticky `1777` that
docker-library uses on purpose so an arbitrary UID can write (php's `/var/www/html`, ruby's `GEM_HOME`, tomcat's
`logs temp work`); world-writable all the same, where `chgrp 0` and `chmod g=u` is the OpenShift way, so they stay.
The worst read: `chmod 777` on `/etc/pki/tls/certs/ca-bundle.crt`, `chmod -R 666` on a configuration directory.
In the validator: 381 actions, 155 fixes.

## H4: every package upgraded in the build (9 Nov)

`SystemUpgrade` (rules, low; information in both shipped profiles): a `RUN` in any stage running a package
manager's upgrade (`apt-get` and `apt` `upgrade`, `dist-upgrade`, `full-upgrade`; `apk upgrade`; `dnf`, `yum` and
`microdnf` `update`, `upgrade`, `distro-sync`; `zypper` `update`, `dup`, `patch`) with no package named. apt's and
apk's `update` only refresh the index and are left alone. The rule reads each `RUN`'s commands through facts'
`RunScripts.commands` (public for it), since facts keeps package names only for installs: in the raw files about 65
of some 490 upgrades name their packages (`yum update -y tzdata`), which are left alone.

Measured on the combined estate: 414 in 407 Dockerfiles (`apt-get upgrade` 224, `microdnf update` 63, `yum update`
59, `dnf update` 34, `dnf upgrade` 17, the rest under 11). The 50-finding sample checked line by line: each `RUN`
holds the reported upgrade with no package named, so no false positives.

