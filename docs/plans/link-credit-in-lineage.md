# Crediting links in lineage: plan

Status: planned 9 Nov; the question answered 9 Nov; built 9 Nov.

## Why

The validator's next slice reads the effective image, and needs to know which Dockerfile each image in the chain
was built from. Lineage writes its links with a tier and scores, but whether a link counts (the crediting rule from
[lineage-from-data](lineage-from-data.md)) is decided in the report, and the validator must not depend on the report.
Asked 9 Nov where the rule should live: in lineage, which writes the decision on every link.

## Design

- `LinkRule` (threshold, margin) moves from `report` to `lineage`, with the same defaults (0.8, 0.05) and the same
  rule: a link is credited when it is STRONG, PROBABLE with one Dockerfile, or WEAK with one Dockerfile scoring at
  least the threshold and beating the runner-up (when there is one) by at least the margin.
- `Link` gains `credited`. The analysis applies the rule to every link before writing, so `links.jsonl`,
  `effective.jsonl` (the chain and each property's `setBy`) and the gaps all carry it.
- `LineageMain` reads `LINK_THRESHOLD` and `LINK_MARGIN`; `ReportMain` stops reading them and `CatalogueSettings`
  loses `linkRule`. The report reads `credited` from `links.jsonl`; a link without it (lineage written before this
  change) is not credited, so stored lineage is rerun, which needs no Artifactory.
- `rates.json` counts the credited links.
- The fixture bank's image expectations gain `credited`, so a change to the rule shows in their diff.

## Tests, in order

1. `LinkRule.credited`: STRONG; PROBABLE with one Dockerfile and with two; WEAK above the threshold with the margin,
   below it, within the margin, without a runner-up; NONE.
2. `LinkRule.credit` marks a link; `Analysis` writes `credited` in `links.jsonl` and in `effective.jsonl`.
3. `LineageRates` counts credited links.
4. `LineageMain` reads the two settings and refuses a value outside 0 to 1, naming it.
5. The corpus expectations carry `credited`.
6. `RegistryImages.linked` reads `credited`; the report's settings lose the rule; its fixtures carry the field.
7. Measured: lineage rerun offline on OKD and the demo; the report's catalogue byte for byte the same as before.

## Measured (9 Nov)

Lineage rerun offline on the stored outputs, then the report, against both written by the code before the change:

- OKD (34 images): `links.jsonl` the same apart from `credited`; 18 credited (`rates.json`), as the report credited
  before; every report file byte for byte the same.
- The sclorg demo (5 images): 3 credited; nginx's exact match to four identical Dockerfiles stays candidates, as
  before; every report file byte for byte the same.
- The fixture bank's 34 images: 22 credited (each a weak link to one Dockerfile), 4 weak links to two Dockerfiles
  and 8 without one not.
