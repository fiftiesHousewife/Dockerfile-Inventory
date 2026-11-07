# Forking the Dockerfile parser: plan

Status: planned 7 Nov; questions answered 7 Nov (a `parser` module here; all 15 bugs fixed now).

## Why

The owner expects no upstream fixes: OpenRewrite is moving to source-available licences. What was found (7 Nov):

- We use `org.openrewrite:rewrite-docker` 8.90.4. Its POM declares the Apache License 2.0; its source is public in
  `openrewrite/rewrite` under `rewrite-docker/` (build commit `398a6349648ca802aa050bdb6fe45a99395dce47`).
- 8.90.4 (24 Aug 2026) is the newest on Maven Central; nothing has been published since, unusual for OpenRewrite.
- The separate `openrewrite/rewrite-docker` repository (recipes) was archived on 26 Jan 2026 and carries the Moderne
  Source Available License; OpenRewrite's licensing page puts its newer language modules under that licence and does
  not name Docker either way.
- We have 15 parser bugs with minimal reproductions (`docs/upstream/rewrite-docker-issues.md`): 12 worked around by
  rewriting text before a second parse (`RetryText`), 3 with no safe workaround, counted as parse failures.

Apache 2.0 allows a fork: copy, change, ship inside the company, keeping the licence and notices and marking what we
changed.

## Design

| # | Step |
| --- | --- |
| F1 | The parser module at 8.90.4 copied from `openrewrite/rewrite@398a634` (`rewrite-docker/`: the ANTLR grammar, the tree, the printer, the visitors), with its `LICENSE` and `NOTICE`, under question 1's home and a new group so it cannot be mistaken for upstream |
| F2 | It still depends on the published `rewrite-core` 8.90.4 (Apache 2.0, unchanged), so facts, rules and the converter change only their dependency |
| F3 | Each of the 15 bugs fixed in the grammar or printer, test-first from its minimal reproduction; as each is fixed, its `RetryText` workaround and its note go (question 2) |
| F4 | `verifyUpToDate` stops checking `rewrite-docker` (it is ours now) but keeps checking `rewrite-core` |
| F5 | Measured on all six estates before and after: parse failures, unreadable RUNs, round trip; the fixture bank (`fixture-bank.md`) pins every case |

## Questions

1. Where the fork lives: a module in this repository (`parser/`, simplest, built and tested with everything else), or
   its own repository published to the internal Artifactory (reusable by other teams, a release step of its own)?
2. Bugs: fix all 15 in the fork and delete `RetryText` as each goes, or fork first with no change and fix them one
   by one later?

Answers (7 Nov): 1, a `parser` module in this repository; 2, fork unchanged first (outputs identical), then fix all 15
test-first, deleting each `RetryText` workaround and its note as its bug is fixed.

