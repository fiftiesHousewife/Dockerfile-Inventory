# The fixture bank

Real Dockerfiles and real images that broke something, each with the outcome the pipeline gives it today. The corpus
tests read every case: facts' `CorpusTest` (parse outcome, unreadable `RUN`s), the converter's `CorpusRoundTripTest`
(round trip) and lineage's `CorpusLinkTest` (the link an image gets). A case whose outcome changes fails its test,
improvement or not, so a fix shows exactly which cases it turned; update `expected.json` when the change is meant.

All files come from public GitHub organisations and public registries; each case records its source. Files with a
secret-shaped value are never collected.

## `dockerfiles/<case>/`

`Dockerfile`, the file as discovery fetched it, and `expected.json`: `source` (repository, path, commit, blob SHA),
`why` it is here, `parse` (`parsed`, `retried` = parses only with a parser workaround, `template`, `failed`),
`unreadableRuns`, `roundTrip` (`passes`, `fails`, `not-parsed`).

| Cases | What they pin |
| --- | --- |
| `failed-*` (35) | every file rewrite-docker 8.90.4 rejects in the six organisations, templates aside (including issues 11, 14 and 15, which have no workaround) |
| `template-*` (5) | templates saved under a Dockerfile's name |
| `unreadable-run-*` (74) | every file with a `RUN` the shell reader cannot follow |
| `roundtrip-*` (8) | files that parse but do not round-trip (a build-argument digest, `RUN --mount` on a classic target) |
| `retried-<kind>-*` (4 per kind) | files that parse only with a parser workaround: `json-comma`, `flag-default`, `arg-names`, `arg-empty`, `lone-backslash`, `comment-in-continuation`, `key-value-comment`, `other` |
| `parsed-plain-*` | a plain file, the control |

Collected from the combined run of 7 Nov (`docs/plans/combined-report.md`) with `scripts/corpus-collect.sh out/all`;
since 9 Nov the collector is `./gradlew collectCorpus --args=out/all` (the `tools` module).

## `images/<case>/`

`config.json`, a real image config trimmed to labels, user, layers and history; `dockerfiles.jsonl`, the facts of every
Dockerfile in the repository the image's source label names (the candidates the linker must choose among); and
`expected.json`: the link's tier and the Dockerfiles it names. Collected from the OKD run (`docs/plans/okd-lineage.md`)
with `scripts/corpus-collect-images.sh out/okd` (since 9 Nov `./gradlew collectImages --args=out/okd`, which also
records `credited`): 26 OKD components, the OKD bases, `centos:stream10` and `stream9`, and
the four sclorg images built with Buildah in the first demo.

The curated fixtures beside each module's tests (`facts/src/test/resources/fixtures/dockerfiles/`, lineage's
`history/`, the contracts in `testdata/contracts/`) stay where they are: they pin one behaviour each, named in their
own READMEs.
