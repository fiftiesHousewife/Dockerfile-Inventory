# Backlog

Phases and gates follow the [delivery plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R). Move an item to Shipped with a date
and one line when its gate is met.

## Active

- Done 7 Nov: a bank of real cases (`docs/plans/fixture-bank.md`): 152 Dockerfiles and 34 images that broke
  something, pinned by corpus tests in facts, the converter and lineage.
- Queued 7 Nov, in order: the parser fork and its 15 fixes
  (`docs/plans/parser-fork.md`); the report's usability (`docs/plans/report-usability.md`); the corporate network
  settings wired into discovery and lineage, Gradle mirrors and the pod image (`docs/plans/corporate-readiness.md`,
  the `http` module built).
- Next (planned 7 Nov, `docs/plans/fifteen-thousand.md`): grow the public estate to about 15,000 locations from
  Red Hat-style organisations (RedHatInsights, stolostron, ...); discovery runs by the owner.
- Next (planned 7 Nov, `docs/plans/corporate-readiness.md`): running inside a corporate network: proxies, a
  corporate CA, token files, retries and rate pacing, restricted access, Gradle mirrors, a pod image.
- Phase 0 — Discovery. Plan: `docs/plans/phase-0.md`. Gate: every repository has an outcome; release-index
  coverage reported; pilot hand check matches.
  - Done: slice 1 (enumeration, release-index scoping, tree listing, blob fetch and dedupe, resume, rate limits).
  - Done: slice 2, walk subtrees of truncated trees (depth limit 8; submodules kept, not listed).
  - Blocked on the GHE token: slice 3 recorded fixtures; slice 4 pilot and full run.
  - Done 6 Nov: eighteen fixes from runs on public `sclorg` repositories, across discovery, facts, rules and the
    converter (`docs/plans/demo-fixes.md`, F1-F18); discovery now skips non-Dockerfile extensions, third-party
    copies and symlinks.
  - Done 6 Nov: fixes from the `openshift` run (`docs/plans/openshift-fixes.md`): unreadable RUNs 10.7% -> 2.7%,
    parse failures 3.4% -> 2.3%, round trip 2,603 -> 2,730 of 2,833. Discovery now
    also skips `*.dockerignore` and test inputs (`testdata/`, `testfiles/`).
  - Done 6 Nov: twelve fixes from a code review of the session's changes (`docs/plans/review-fixes.md`): secret
    values hidden in the dashboard, a converter crash, multi-value `ARG`s read wrongly, and quoting edge cases.
  - Done 6 Nov: `konflux-ci` (158 repositories, 3,231 locations, 722 files): parse failures 0.7% -> 0.1%, round trip
    714 -> 720 of 722 (`docs/plans/more-estates.md`).
  - Done 6 Nov: `redhat-developer` (288 repositories, 604 locations, 518 files): every file that parses passes the
    round trip, after the converter reads `ENV` from its text as it does `LABEL`; discovery skips image and grammar
    files named like Dockerfiles.
  - Done 6 Nov: `docker-library` (48 repositories, 594 locations, 581 files): no parse failures; 35% of its RUNs were
    unreadable, which led to S4 (`docs/plans/shell-reading.md`): groups, `case`, loops and functions read, PowerShell
    counted apart; every estate now 0-1.2% unreadable. Round trip 580 of 581 after a fix for Windows `COPY`
    destinations ending in a backslash (mongo).
  - Done 6 Nov: a cleanup of the parsing code (`docs/plans/parsing-cleanup.md`): shared `Continuations`,
    `KeyValuePairs` and `Expansions`; `FactsParser` split into `RetryText`, `Templates` and `MergedLines`;
    `ShellTokeniser` into `ShellWords` and `ShellCommands`. openshift's and docker-library's facts, rules and round
    trips are unchanged, apart from four Windows images whose `COPY` destination now keeps both trailing backslashes.
  - Done 7 Nov: `bitnami` (22 repositories, 266 locations, 266 files): no parse failures, round trip 266 of 266.
    Bash arrays are read (its 257 unreadable RUNs, 15.5%, now 0) and minideb's `install_packages` counts as an apt
    install (package calls 648 -> 1,110). R1 no longer reports `FROM scratch` (524 false positives in konflux-ci, 174
    in openshift) and S2 leaves a secret-named value that is only a path alone (`docs/plans/bitnami-fixes.md`).
  - Built 7 Nov, OKD lineage (`docs/plans/okd-lineage.md`): lineage cuts the wrapper Red Hat's release tooling puts
    around a Dockerfile's steps, a source label limits an image's link to that repository, `scripts/okd-select.sh`
    picks 30 images of OKD 5.0.0-okd-scos.5 and their release index, and `lineage-demo.sh` mirrors them unchanged.
    Run 7 Nov: every parent right; links 14 of 26 at first, 26 of 26 after reading build-argument prefixes, leaving
    `ARG` out and comparing labels as a set (owner's choice). Q12's outdated-base rule misleads where one repository
    holds different images under different tags (Konflux's `art-okd-images`): noted, not changed.

- Phase 1 — Parse and normalise. Plan: `docs/plans/phase-1.md`. Gate: parse-failure and opaque-`RUN` rates
  published. Code complete; the gate waits on a discovery run.
  - Done: slice 1, `facts` module and parser adapter; slice 2, shell tokeniser; slice 3, package calls; slice 4, fact extraction; slice 5, normal forms; slice 6, the facts run and rates.
  - Fixed 6 Nov: a comment line inside a continued instruction was recorded as a parse failure.
  - To raise upstream (the owner's call, not filed): fifteen rewrite-docker 8.90.4 issues, drafted with
    reproductions in `docs/upstream/rewrite-docker-issues.md`; twelve are worked around here, three (a heredoc after
    a command in `RUN`, a quoted `COPY` heredoc delimiter, `ENV KEY = value`) are not and those files count as parse
    failures.

- Phase 1b — Lineage and effective images. Plan: `docs/plans/phase-1b.md` (questions answered 6 Nov). Gate:
  lineage for at least 95% of Artifactory images; images matched to Dockerfiles by fingerprint, unmatched images
  published as the gap list.
  - Tried 6 Nov against a real Artifactory (local JCR 7.77, `scripts/lineage-demo.sh`): four sclorg images built
    with Buildah from discovered Dockerfiles; every parent right, each image linked to its own Dockerfile
    (PROBABLE, as no release index). nginx 1.24 also linked to 1.20, 1.22 and c8s files whose steps differ only in
    ENV values, which the normal form leaves out.
  - Done 6 Nov: the report's lineage section, and fixtures recorded from the JCR run: an end-to-end lineage test on
    JCR's real replies and four contracts on real Buildah history (`docs/plans/lineage-from-jcr.md`). Later: keep
    `ENV` values in the shared normal form (the nginx case above).
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
  - Done 6 Nov: a more visual dashboard (`docs/plans/report-visuals.md`): health strip with the owner's thresholds,
    archetype coverage curve, bases as bars by end-of-life status, stacked bars, archetype cards with an example
    Dockerfile (secret values hidden), info markers on every number and section, and the lineage section.
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
