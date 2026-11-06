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
  - To raise upstream (rewrite-docker 8.90.4): an unrecognised line is appended to the previous argument
    silently; a `# syntax=` line after the first instruction is rejected, though Docker reads it as a comment.

- Phase 1b — Lineage and effective images. Plan: `docs/plans/phase-1b.md` (questions answered 6 Nov). Gate:
  lineage for at least 95% of Artifactory images; images matched to Dockerfiles by fingerprint, unmatched images
  published as the gap list.
  - Done: slice 1, the Artifactory REST client (`lineage` module), against hand-written fixtures.
  - Done: slice 2, image nodes: one tag to its amd64 config, or a counted skip.
  - Done: slice 3, the family tree from layer prefixes (OCI chain IDs), ambiguous parents reported.
  - Next: slice 4, history normaliser, from public Buildah-built images. Recorded fixtures wait on an
    Artifactory token.

## Queued

- Phase 2 — Cluster and report. Gate: written decision, base-image route or DSL route.
- Phase 3 — Quality rules. Gate: false-positive rate per rule below threshold before auto-fix.
- Phase 4 — DSL, builders, renderer.
- Phase 5 — Converter. Gate: round-trip equivalence; opt-in per team.

## Shipped

- 2026-11-05 Repository scaffold: Gradle 9.4.1, build-logic convention plugin, discovery module.
