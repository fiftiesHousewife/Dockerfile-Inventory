# The validator's reach through archetypes and bases: plan

Status: planned 9 Nov; the questions answered 9 Nov; built 9 Nov.

## Why

The action plan was to show each fix once with everything it reaches (answered 8 Nov: by fix for the platform team,
by repository for each team). Today every action is one Dockerfile and one check, ordered by its own locations, so a
fix repeated across an archetype's copies, or made once in a base, shows as many unrelated actions.

## Measured before (9 Nov)

From the catalogue stage and the validator on stored facts:

| | actions | on archetype members (more than one Dockerfile) | (archetype, check) groups | on a Dockerfile others are built on |
| --- | --- | --- | --- | --- |
| combined estate | 8,777 | 5,464 | 1,083 | 74 (P8 46, P2 12, P6 8, P1 7, P9 1) |
| OKD | 4,604 | 2,724 | 595 | 6 |

So the archetype is where the reach is: three in five actions are copies of a fix to one template. Bases matter less
here, and whether a fix to a base reaches the images on it depends on the check: a child inherits its base's user
only when it sets none, its start command only when it sets none, and never its own `FROM`'s pin.

## Design

- `ValidatorMain` reads an optional `CATALOGUE_DIR` (the catalogue stage's `OUT_DIR`): `archetypes.jsonl` for each
  Dockerfile's archetype, `catalogue/dockerfiles.jsonl` for the Dockerfiles built on it.
- `fixes.jsonl` (asked 9 Nov) beside `actions.jsonl`: one fix per (archetype, check), the archetype's representative
  when it has the action (else the copy with the most locations), every copy, and the Dockerfiles and locations it
  reaches; ordered by severity, then locations reached, then certainty, check and representative. A Dockerfile in no
  archetype of several is a fix of its own. `actions.jsonl` stays one per Dockerfile, for the view by repository.
  `action-plan.json` counts the fixes.
- A fix reaches each copy, and through each copy the Dockerfiles built on it that inherit what the fix changes (asked
  9 Nov: per check), followed down while each one inherits it:

  | Check | A Dockerfile built on the fixed one inherits the fix |
  | --- | --- |
  | P1 | when it sets no `USER` |
  | P4, P6, P10 | when it sets no `ENTRYPOINT` or `CMD` (P4 counts conservatively: sudo installed in the base reaches it either way) |
  | P2 | unless it does group-0 permission work of its own (`fix-permissions`, a group-writable `chmod` or `COPY --chmod`): found measuring, below |
  | P3, P5, P9 | always: the exposed ports, setuid files, `HEALTHCHECK` and `VOLUME`s come with the base |
  | P7, P8 | never: its own `FROM` is its own |

  The question as asked said never for P3; exposed ports are inherited, so P3 counts always.
- Without `CATALOGUE_DIR` each action is a fix of its own, reaching its own locations.
- The report's page leads with the fixes.

## Tests, in order

1. `Catalogue.read`: each Dockerfile's archetype and the archetype's representative, from `archetypes.jsonl`; the
   Dockerfiles built on each, from the catalogue's `links.children`.
2. `Inheritance.inherits(check, child)` per the table.
3. `Fixes.of`: actions on an archetype's copies grouped per check, the representative chosen, a Dockerfile in no
   archetype alone; the reach through children that inherit, and not through one that does not; the order.
4. `ValidatorRun` writes `fixes.jsonl` and counts the fixes; `ValidatorMain` reads `CATALOGUE_DIR`.
5. The report's page reads `fixes.jsonl` and leads with the fixes.
6. Measured: OKD and the combined estate. (The ratchet runs without a catalogue, which a validator test cannot
   build, so its fixes would equal its actions; it is left as it is.)

## Questions answered

1. Actions on an archetype's copies: `fixes.jsonl` beside `actions.jsonl` (9 Nov).
2. A fix to a base counts the Dockerfiles built on it that inherit what it changes, per check (9 Nov).

## Measured (9 Nov)

The catalogue stage, then the validator with `CATALOGUE_DIR` (and on OKD `LINEAGE_DIR`), then the report:

| | actions | fixes | of several copies | Dockerfiles reached by inheritance |
| --- | --- | --- | --- | --- |
| combined estate | 8,777 | 4,396 | 983 | 77 (P2 62, P1 8, P6 4, P9 3) |
| OKD | 4,604 | 2,475 | 525 | 8 (P2 4, P1 1, P9 3) |

- With P2 counted always, a fix to sclorg's s2i-core reached 91 Dockerfiles, but s2i-base runs `fix-permissions`
  over the same tree, so it and everything on it no longer need the fix: only 20 of the 91 had a P2 finding of their
  own. P2 now stops at a Dockerfile doing group-0 permission work itself; P2's inherited reach on the combined estate
  127 -> 62. A sample of six P2 fixes with inherited reach read right.
- Sampling also found a P2 false positive: python 3.12-minimal runs `fix-permissions ${APP_ROOT}`, a variable the
  check does not expand; queued as the next fix.
- The report on OKD: `openshift.html` leads with "4,604 actions come to 2,475 fixes", the first a P1 fix to one
  Dockerfile with 74 copies over 96 locations; 254 links, none broken. The catalogue the report writes is byte for
  byte the catalogue stage's.
- `./gradlew validate` unchanged.

## Process

The fixes' tests were written one behaviour at a time and each seen to fail; the P2 inheritance rule was changed
after the measurement, with its test first. `Fix.because` was added for the page with its test first.
