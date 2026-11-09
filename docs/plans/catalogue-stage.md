# The catalogue as a stage of its own: plan

Status: planned 9 Nov; the question answered 9 Nov; built 9 Nov.

## Why

The validator's next slice counts each action's reach: one fix to a base, or to an archetype's template, with every
Dockerfile and location it reaches. The catalogue already holds that (`links`, `actions.reach`; `archetypes.jsonl`),
but the report writes the catalogue and also reads the validator's output, so the validator could not read the
catalogue without the report running twice. Asked 9 Nov: a stage of its own, run before the validator.

## Design

- `CatalogueMain`, a second entry point in the `report` module, writes `passes.json`, `archetypes.jsonl` and
  `catalogue/dockerfiles.jsonl` under `OUT_DIR`, and no pages. Its settings are the report's, without
  `VALIDATOR_DIR`.
- `ReportMain` is unchanged: it still writes the catalogue as well as the pages, so a run without the validator needs
  one stage; the catalogue is deterministic, so the two write the same files.
- The pipeline with the validator: `catalogue`, then `validator` (reading it as `CATALOGUE_DIR`, next slice), then
  `report`.
- The report distribution gains `bin/catalogue`; `inventory catalogue` runs it.

## Tests, in order

1. `ReportRun.catalogue` writes the catalogue, the archetypes and the passes, and no page.
2. `CatalogueMain` reads the settings and refuses a missing one by name.
3. The start script: `installDist` holds `bin/catalogue` (checked by hand, and in the image by `inventory catalogue`).
4. Measured: OKD's catalogue from `catalogue` byte for byte the same as the report's.

## Measured (9 Nov)

OKD with lineage: `catalogue` wrote `catalogue/dockerfiles.jsonl`, `archetypes.jsonl` and `passes.json` byte for byte
the same as the report's. `scripts/inventory` tried on stub distributions: `catalogue` runs the report's
`bin/catalogue`, other stages their own, an unknown one is refused.
