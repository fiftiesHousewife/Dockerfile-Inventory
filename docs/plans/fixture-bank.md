# A bank of real cases: plan

Status: built 7 Nov.

The owner asked whether we have a really good bank of fixtures for every case that breaks parsing, lineage and the
round trip. We do not. Seven estates and about sixty fixes later, the real cases that broke something are mostly
tested with short inline strings written from them, and the cases still failing are not pinned at all:

| | Real cases seen | Real fixtures |
| --- | --- | --- |
| Parse failures (combined estate, 7 Nov) | 35, plus 53 templates | 12 Dockerfiles in `facts/.../fixtures/dockerfiles/` |
| Unreadable `RUN`s | 81 | none as files |
| Round trip failures | 8 that parse (7 a build-argument digest, 1 `RUN --mount` on a classic target) | none |
| Lineage | Buildah, Konflux, OKD release tooling, JCR histories | 5 history configs, the OKD config, the JCR run, 6 contracts |
| Past fixes (demo F1-F18, openshift S1-S3, konflux, redhat-developer, docker-library S4, mongo, bitnami B1-B4) | about 60 | mostly inline strings |

This bank is also what the parser fork (`parser-fork.md`) is measured against: each of the 15 parser bugs must have
its real lines here before it is fixed, so the fix is seen to turn a failure into a pass.

## Design

`testdata/corpus/`, shared by facts, the converter and lineage (declared as a test input in each, as the contracts
are):

```
testdata/corpus/dockerfiles/<case>/Dockerfile       a real file from a public estate, whole or trimmed to the lines
                                                    that matter (line numbers kept where the case is about them)
testdata/corpus/dockerfiles/<case>/expected.json    what each stage must say: parse (parsed | template | failed and
                                                    the reason's class), unreadable RUNs, packages found, round trip
                                                    (passes | fails and why), and the issue or fix it pins
testdata/corpus/images/<case>/config.json           a real image config (history, labels, layers), trimmed
testdata/corpus/images/<case>/expected.json         own steps, the Dockerfile case it links to and the tier
testdata/corpus/README.md                           every case: source (organisation, repository, path, commit),
                                                    why it is here, what it pins
```

- One parameterised test per stage reads every case and checks its expectation: facts (`CorpusTest` in facts), the
  converter (round trip), lineage (own steps and links against the corpus Dockerfiles).
- A ratchet: a case expected to fail that starts to pass fails the test too, with the message "now passes: update
  expected.json". A fix then shows exactly which cases it turned, and nothing improves or regresses unseen.
- Cases, collected by a script from the outputs we already have (`scripts/corpus-collect.sh`, kept for the 15,000
  run): every current parse failure that is not a template (35), five templates of different kinds, every distinct
  shape of unreadable `RUN` (one file per shape, about 20), every round trip failure that parses (8), one real file
  per past fix (from its plan's notes and the estates' blobs), the 15 parser issues' real lines, the OKD and JCR images
  and the contracts' images.
- Secrets: a case whose file has a secret-shaped value (rule S2) has the value replaced by `REDACTED` of the same
  shape class before it is stored; the collector refuses to write a file S2 still flags.
- Public files only (the six public organisations so far); their organisation, repository, path and commit recorded.

## Tests, in order

1. The corpus reader: a case folder -> its Dockerfile and expectation; a missing expectation is an error.
2. Facts' corpus test over two hand-picked cases (one parsing, one failing), then the collected set.
3. The ratchet: an expected failure that passes fails the test with its case name.
4. The converter's corpus test (round trip).
5. Lineage's corpus test (own steps and links).
6. The collector run on the combined estate; the README lists every case; `./gradlew check` green.

## As built

- `ParseResult.Parsed` and each facts row say whether the file parsed only on the retry (`retried`); `rates.json`
  counts them (`parsedOnRetry`): 354 of the combined estate's 5,250 files lean on a workaround.
- `testdata/corpus/dockerfiles/`: 152 real cases (35 failed, 5 templates, 74 with an unreadable `RUN`, 8 round-trip
  failures, 29 retried across eight workaround kinds, a control), collected by `scripts/corpus-collect.sh out/all`.
- `testdata/corpus/images/`: 34 real images from the OKD run with their candidate Dockerfiles, collected by
  `scripts/corpus-collect-images.sh out/okd`.
- Corpus tests in facts (`CorpusTest`, with `Corpus` naming outcomes), the converter (`CorpusRoundTripTest`,
  `CorpusOutcome`) and lineage (`CorpusLinkTest`); each fails when a case's outcome changes, naming the case (checked by
  breaking one expectation).
- Secret check: S2's files are never collected, and the corpus holds no key, token or JWT shape (scanned).
- Per-fix cases (demo F1-F18 and later rounds) are covered by kind rather than one by one: most of those fixes were
  workarounds, now pinned through `retried-*`, or shell-reading fixes, whose remaining failures are the
  `unreadable-run-*` cases; their original tests stay in each module.

