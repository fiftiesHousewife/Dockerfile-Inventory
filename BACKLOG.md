# Backlog

Phases and gates follow the [delivery plan](https://claude.ai/artifact/N7dHR2cH1nKgAhqkpBJx6R). Move an item to Shipped with a date
and one line when its gate is met.

## Active

- Phase 0 — Discovery. Plan: `docs/plans/phase-0.md`. Gate: every repository has an outcome; release-index
  coverage reported; pilot hand check matches.
  - Done: slice 1 (enumeration, release-index scoping, tree listing, blob fetch and dedupe, resume, rate limits).
  - Done: slice 2, walk subtrees of truncated trees (depth limit 8; submodules kept, not listed).
  - Blocked on the GHE token: slice 3 recorded fixtures; slice 4 pilot and full run.

## Queued

- Phase 1 — Parse and normalise. Gate: parse-failure and opaque-`RUN` rates published.
- Phase 1b — Lineage and effective images. Gate: lineage for at least 95% of Artifactory images;
  images matched to Dockerfiles by fingerprint, unmatched images published as the gap list.
- Phase 2 — Cluster and report. Gate: written decision, base-image route or DSL route.
- Phase 3 — Quality rules. Gate: false-positive rate per rule below threshold before auto-fix.
- Phase 4 — DSL, builders, renderer.
- Phase 5 — Converter. Gate: round-trip equivalence; opt-in per team.

## Shipped

- 2026-11-05 Repository scaffold: Gradle 9.4.1, build-logic convention plugin, discovery module.
