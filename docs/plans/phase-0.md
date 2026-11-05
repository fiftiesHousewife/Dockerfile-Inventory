# Phase 0 — Discovery: plan

Status: slices 1 and 2 done. Slices 3 and 4 wait on the GHE token.

## Scope (settled 5 Nov)

Dockerfiles only. Out of scope for discovery, by decision:

- Pipelines, Jenkinsfiles and other builders: images are built by Tekton from the Dockerfiles; stick to Dockerfiles.
- Owners and CODEOWNERS: fixes go through one central PR stream, and GitHub routes reviews itself.
- Business units: not a requirement. Several organisations, reported as one estate.
- User-namespace repositories: organisations only.
- Artifactory reconciliation: moves to Phase 1b, where images are matched to Dockerfiles by fingerprint
  (layer-prefix lineage and build-history alignment). Images that no Dockerfile matches are the gap list.

Other answers: one released commit per repository, keyed `owner/repo` (as built); the output directory is a
persistent volume, so resume works across pod restarts.

**Phase 0 gate:** every repository in every organisation has a recorded outcome; no repository is left
`TRUNCATED` except past the depth limit; release-index coverage is reported (released versus default-branch
scope); the pilot's hand check matches.

## Slice 2 — Truncated trees: walk subtrees (done)

As built: test 4 folded into a submodule test (entries of type `commit` are kept, never listed as trees);
test 8 passed on first run because its branch was written during the interface refactor, so it was checked
by mutation instead; refactoring added one test, a subtree that vanishes mid-scan fails the repository as a
`GitHubException` instead of aborting the run.

When the recursive listing comes back truncated, list the root non-recursively, then each subtree recursively,
splitting a subtree one level further only when its own recursive listing is truncated. HTTP only.

Design:

- `GitHub.tree(repository, treeSha, recursive)` replaces `tree(repository, commitSha)`. The trees API accepts a
  commit SHA or a tree SHA; subtrees are addressed by the tree SHA from the parent listing.
- `TreeWalk` (new, package-private) owns the descent: given the root commit, returns one `Tree` of full-path
  entries, `truncated` only if a subtree is still truncated at the depth limit (8).
- `RepoScan` calls `TreeWalk` instead of `gitHub.tree` directly. Subtree calls share the scan's semaphore permit
  (one repository's walk is sequential).

Tests, in the order they will be written:

1. `TreeWalk` returns the recursive listing unchanged when it is not truncated (one call).
2. A truncated recursive listing leads to one non-recursive root listing, then a recursive listing per subtree.
3. Subtree entries come back with the subtree's path as prefix (`svc/a/Dockerfile`).
4. Root-level blobs from the non-recursive listing are kept.
5. A truncated subtree is split one level further.
6. Past the depth limit, the result is `truncated` and keeps what was listed.
7. `RepoScan` reports `SCANNED` with Dockerfiles from every subtree of a truncated repository.
8. `GitHubHttp.tree` omits `recursive=1` when `recursive` is false (local server test).

## Slice 3 — Recorded fixtures (needs the GHE token)

Replace each hand-written file in `discovery/src/test/resources/fixtures/ghe/` with a recorded response from
the real server, trimmed and anonymised: organisations page, repositories page, a tree, a truncated tree,
a blob batch, a GraphQL error. Existing tests must pass unchanged; a test that fails reveals a wrong assumption
about the response shape and is fixed in the code, not the fixture.

Also measure: `GET /api/v3/rate_limit` for the scanner token; record the limits in AGENTS.md.

## Slice 4 — Pilot (needs the GHE token and the release index export)

1. Run against one organisation; record wall time, calls made, rate-limit headroom.
2. Hand-check five repositories against the GitHub UI: Dockerfiles found, commit scanned, scope flag.
3. Check 20 release-index SHAs resolve (`RELEASE_COMMIT_MISSING` count is zero, or each is explained).
4. Full estate run; publish counts: repositories by outcome, Dockerfiles, unique blobs, release-index coverage.

## Open questions

None blocking slice 2. Slice 3 and 4 wait on the scanner token and release-index export (see the delivery
plan's "Before Phase 0" checklist).
