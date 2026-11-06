# Backlog

Phases and gates follow the [delivery plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R). Move an item to Shipped with a date
and one line when its gate is met.

## Active

- Phase 0 — Discovery. Plan: `docs/plans/phase-0.md`. Gate: every repository has an outcome; release-index
  coverage reported; pilot hand check matches.
  - Done: slice 1 (enumeration, release-index scoping, tree listing, blob fetch and dedupe, resume, rate limits).
  - Done: slice 2, walk subtrees of truncated trees (depth limit 8; submodules kept, not listed).
  - Blocked on the GHE token: slice 3 recorded fixtures; slice 4 pilot and full run.

- Phase 1 — Parse and normalise. Plan: `docs/plans/phase-1.md`. Gate: parse-failure and opaque-`RUN` rates
  published. Code complete; the gate waits on a discovery run.
  - Done: slice 1, `facts` module and parser adapter; slice 2, shell tokeniser; slice 3, package calls; slice 4, fact extraction; slice 5, normal forms; slice 6, the facts run and rates.
  - Fixed 6 Nov: a comment line inside a continued instruction was recorded as a parse failure.
  - To raise upstream: seven rewrite-docker 8.90.4 issues, drafted with reproductions in
    `docs/upstream/rewrite-docker-issues.md`; each is worked around here.

- Phase 1b — Lineage and effective images. Plan: `docs/plans/phase-1b.md` (questions answered 6 Nov). Gate:
  lineage for at least 95% of Artifactory images; images matched to Dockerfiles by fingerprint, unmatched images
  published as the gap list.
  - Done: slice 1, the Artifactory REST client (`lineage` module), against hand-written fixtures.
  - Done: slice 2, image nodes: one tag to its amd64 config, or a counted skip.
  - Done: slice 3, the family tree from layer prefixes (OCI chain IDs), ambiguous parents reported.
  - Done: slice 4, history normaliser, from public Konflux/Buildah-built UBI images.
  - Done: slice 5a, `facts` writes each Dockerfile's steps and base path, held to lineage's normal form by a
    contract from a real Dockerfile and its real build history.
  - Done: slice 5b, links: exact (strong or probable), weak by ordered alignment on the same base, none, and the
    "parent missing" finding.
  - Done: slice 6, the effective fold: each image's properties with the image (and Dockerfile group) that set them.
  - Done: slice 7, gaps, external bases and outdated bases.
  - Done: slice 8, the run: inventory (resumable, concurrent), analysis and rates, `LineageMain`.
  - Code complete. The gate waits on an Artifactory token: recorded fixtures, a real run, the gap list reviewed.

- Phase 2 — Cluster and report. Plan: `docs/plans/phase-2.md` (questions answered 6 Nov). Gate: written
  decision, base-image route or DSL route.
  - Done: slice 1, `report` module and inputs; slice 2, exact passes; slice 3, near-duplicates (MinHash, LSH);
    slice 4, archetypes, k80 and k95; slice 5, base catalogue with the internal end-of-life list.
  - Done: slice 6, CSV tables and DDL for Oracle (loading undecided).
  - Done: slice 7, the HTML dashboard.
  - Done: slice 8, `ReportRun` and `ReportMain`.
  - Code complete. The gate (the written decision) needs real discovery and facts runs. Open: how the tables
    reach Oracle (Q1).

- Phase 3 — Quality rules. Plan (detect-only): `docs/plans/phase-3.md` (questions answered 6 Nov). Gate:
  false-positive rate per rule below threshold before auto-fix.
  - Done: slice 1, harness; slice 2, R1, H1, H2, I1, I2 from the facts; slice 3, S2, S3, H3, R3 over the
    parsed Dockerfile, with line numbers.
  - Done: slice 4, S1 (approved list) and R2 (end-of-life list).
  - Done: slice 5, effective S4 (arbitrary UID) and S1 (chain root), naming the Dockerfile to change.
  - Done: slice 6, run and outputs (findings, table, rates, samples); slice 7, Renovate preset; slice 8,
    `RulesMain`.
  - Detection complete. Next: run it on real output, classify each rule's sample, then plan fixes (Phase 3b).

- Phase 4 — DSL, builders, renderer. Plan: `docs/plans/phase-4.md` (questions answered 6 Nov). Gate: every golden
  passes the Phase 3 rules with zero findings; builders refuse each invariant breach. Phase 4b (central generator
  repository and PR job) waits on a GHE token with write access.
  - Done: model, package installs, renderer with targets, builders with policy, four goldens; the gate's tests
    pass; adjacent RUNs are joined in `build()` (H3), 6 Nov. Next: real archetypes as goldens after the first
    Phase 2 run.

- Phase 5 — Converter. Plan: `docs/plans/phase-5.md` (questions answered 6 Nov). Gate: round-trip equivalence;
  opt-in per team. Phase 5b (building and comparing images, delivering PRs) needs Buildah and Phase 4b.
  - Done: faithful conversion, round trip, JavaPoet classes with TODOs, coverage and raw-reason histogram,
    `ConverterMain`. Next: run on real discovery output; Phase 5b.

## Queued


## Shipped

- 2026-11-05 Repository scaffold: Gradle 9.4.1, build-logic convention plugin, discovery module.
