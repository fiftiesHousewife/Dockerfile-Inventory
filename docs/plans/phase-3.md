# Phase 3 — Quality rules, detect-only: plan

Status: planned 6 Nov; questions answered. All slices built 6 Nov; enabling fixes waits on classified samples. This plan covers detection. Fixes are enabled one rule at a time only
after people have classified a sample of that rule's findings (the false-positive gate), and H1's fix renders
through Phase 4's renderer, so fixes get their own plan.

Every rule first runs detect-only across the estate. Module: `rules`, which may import OpenRewrite and depends on
`facts` for its parser adapter. Inputs, all files: `facts.jsonl` and `parse-failures.jsonl`, discovery's `blobs/`
and `manifest.jsonl`, and, when present, lineage's `effective.jsonl` and `links.jsonl`. Order follows the 5 Nov
decision: security, then speed of remediation, then hygiene.

## Answers (6 Nov)

| # | Question | Answer | What it changes |
| --- | --- | --- | --- |
| Q1 | What runs the recipes | Our own runner | `rules` runs in-process over each distinct Dockerfile once, as facts does; no clones |
| Q2 | Secret-shaped (S2) | Name and value patterns | A name part (split on `_`, `-`, `.`) is `KEY`, or contains `PASSWORD`, `PASSWD`, `SECRET`, `TOKEN` or `CREDENTIAL` (6 Nov: `KEY` inside `VALKEY` was a false positive on sclorg), and the value is a non-empty literal (no variable); or the value has a known secret shape (private key header, AWS access key, JWT) under any name. Only a SHA-256 of the value and its line are kept |
| Q3 | S4 check | Numeric user and group 0 | Finding unless the effective user is numeric and non-root and the chain makes writable paths group-0 writable (`chgrp ... 0`, `chmod g=u` or `g+w`, `COPY --chown=...:0`) |
| Q4 | Renovate | A shared preset | This repo provides a Renovate preset (Dockerfile digest pinning, grouped base updates); the central instance applies it. R1 and R2 are also reported as findings |

## Decisions taken without asking (say if any is wrong)

| # | Decision | Why |
| --- | --- | --- |
| D1 | The approved-base list for S1 is a file, `ALLOW_LIST` (`imagePath,tagPrefix` per line), written from the Phase 2 catalogue after review. Without it, S1 does not run and the rates say so | The list does not exist until people review the catalogue |
| D2 | R2 uses the internal end-of-life list from Phase 2 (`EOL_LIST`), not rewrite-docker's bundled list | One source of truth for end of life (Phase 2, Q4) |
| D3 | Rules that facts already answer (R1, H1, H2, I1, I2) read `facts.jsonl`; the rest read the parsed Dockerfile | No second parse where the facts already hold the answer |
| D4 | A finding is per distinct Dockerfile (blob), with its line where known; the tables expand it to every location | The estate is counted by location (Phase 2, Q3), but a fix is made once per file |
| D5 | Effective findings name the Dockerfile group to change: the `setBy` image's link from the fold | The rule's point: fix the base that set the user, not every child |

## Rule catalogue

| ID | Rule | Kind | Severity | Source |
| --- | --- | --- | --- | --- |
| S1 | Base image not on the approved list | Per-file, and effective for the chain's root | High | facts base, `ALLOW_LIST`; lineage |
| S2 | Secret-shaped `ENV` or `ARG` value | Per-file | Critical | parsed Dockerfile |
| S3 | `curl` or `wget` piped to a shell | Per-file | High | raw `RUN` text (the tokeniser makes such a body opaque) |
| S4 | Not runnable as an arbitrary non-root UID | Effective | High | `effective.jsonl`, chain steps |
| R1 | Base not pinned by digest | Per-file | Medium | facts base |
| R2 | End-of-life base | Per-file | Medium | facts base, `EOL_LIST` |
| R3 | Missing OCI revision and source labels | Per-file | Low | parsed Dockerfile (final image's labels) |
| H1 | RPM package install without a clean in the same `RUN` | Per-file | Low | facts package calls |
| H2 | Shell-form entrypoint | Per-file | Low | facts |
| H3 | Adjacent `RUN`s that could combine | Per-file | Low | parsed Dockerfile |
| I1 | OS packages without a version | Per-file | Info | facts package calls |
| I2 | Unparseable Dockerfile | Per-file | Medium | `parse-failures.jsonl` |

## Slices (test list per slice, in order)

1. **Harness.** `rules` module; `Rule` (id, kind, severity, check), `Finding` (rule, blob, line, message, value
   hash, contributing group), `RuleRun` collecting findings over the estate.
   1. A rule's findings carry its id and severity.
   2. A rule that throws on one Dockerfile records an error finding for that file and the run continues.
2. **Facts rules**: R1, H1, H2, I1, I2.
   3. R1: a base with no digest; an unresolved base is not reported here.
   4. H1: an RPM install (`dnf`, `yum`, `microdnf`) not cleaned in the same `RUN`; `apk --no-cache` and cleaned
      calls are not.
   5. H2: shell-form final entrypoint.
   6. I1: install packages without a version (`git`, not `git-2.43.5` or `git=2.43`).
   7. I2: one finding per parse failure, with its message.

   As built: H1 covers every stage, since a builder stage's cache costs build time even if it never ships. I1
   reads `name-1.2` (rpm) and `name=1.2` (apt, apk) as versioned; an rpm name with a digit after a hyphen
   (`java-17-openjdk`) reads as versioned too, so I1 can miss it, which is acceptable for an Info rule. H2, I1 and
   I2's tests were written together, as three independent rules, and each was seen to fail before its code.
3. **Text rules**: S2, S3, H3, R3, over the parsed Dockerfile.
   8. S2 by name: `ENV DB_PASSWORD=hunter2` and `ARG API_TOKEN=abc`; not `ENV PASSWORD_FILE=$X` or an empty value.
   9. S2 by value shape under any name; the finding holds the hash and line, and the value appears nowhere in
      the output.
   10. S3: `curl ... | sh`, `wget -O- ... | bash`, with continuations; not `curl -o file`.
   11. H3: two adjacent shell-form `RUN`s in one stage; not across a `COPY`, nor when either has `--mount`.
   12. R3: final image without `org.opencontainers.image.revision` or `.source`; a `LABEL` in an earlier stage the
       final stage builds on counts.

   As built: line numbers come from `Lines`, which finds each instruction's own text in the printed file where it
   starts a line (the tree stores no lines, and `printTrimmed` keeps the comments before an instruction). Probing
   found two more rewrite-docker quirks. Variables inside an `ENV` value are plain text, and an empty `KEY=` gets
   the value `=`, so S2 counts any `$` outside single quotes as a variable and `=` as empty. `LABEL a=$X b=$Y` is
   folded into one pair, so R3 reads label keys from the printed instruction. S2's name list includes `KEY`,
   which will flag public key IDs such as `GPG_KEY`; the false-positive sample will show how often.
4. **List rules**: S1 and R2.
   13. S1: a base whose path and tag prefix are not on the list; without a list, S1 is skipped and says so.
   14. R2: a base the end-of-life list marks `eol`.

   As built: both lists read like Phase 2's catalogue: longest tag prefix on a version boundary, path without
   registry host (facts' `fromPath`). S1 also reports a base set by a build argument with no default, since an
   unknown base cannot be shown to be approved.
5. **Effective rules**: S4, and S1 for the chain's root, from lineage output.
   15. S4: an effective user that is root, named or unresolved; the finding names the group that set it.
   16. S4: a numeric user with no group-0 permission step anywhere in the chain; with one, no finding.
   17. S1 effective: a chain whose root's base path is not approved.
   18. Without lineage output, effective rules are skipped and the rates say so.

   As built: S4's group-0 check reads the raw text of every Dockerfile linked in the chain, since the normal-form
   steps and Buildah's history both drop `COPY --chown`. An effective finding's `blobSha` is the image's own
   Dockerfile (its first linked blob) and `contributing` the group to change: the one that set the user, or for a
   missing group-0 step the image's own, or for S1 the Dockerfile built directly on the root. Test 18 belongs to
   the run, so it moves to slice 6.
6. **Outputs.** `findings.jsonl`; `tables/findings.csv` expanded to locations with its DDL; `rates.json` (findings and
   files per rule); `samples/<rule>.csv`, up to 50 findings per rule spread across organisations, from a fixed
   seed, for people to classify.
   19. The CSV has one row per finding per location.
   20. Samples take findings round-robin across organisations and are the same on every run.
   21. No secret value appears in any output.
7. **Renovate preset.** `renovate/dockerfile-estate.json`: Dockerfile digest pinning on, base-image updates grouped
   into one PR per repository, schedule left to the instance.
   22. The preset is valid JSON and pins digests for the `dockerfile` manager.
8. **Run.** `RulesMain`: `DISCOVERY_DIR`, `OUT_DIR`, `[LINEAGE_DIR]`, `[ALLOW_LIST]`, `[EOL_LIST]`. Untested,
   like the other mains.

   As built (slices 6 to 8): the run re-parses each blob and takes its facts from that parse, not from
   `facts.jsonl`, so facts and tree always agree and no `FACTS_DIR` is needed (a change to D3). Effective rules
   read lineage's `effective.jsonl` and `images.jsonl`, and the raw text of linked blobs. Checked end to end with
   `./gradlew :rules:run` on sample Dockerfiles.
