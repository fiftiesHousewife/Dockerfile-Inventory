# The report as a view of the catalogue: plan

Status: planned 8 Nov; questions open. Follows the catalogue (`catalogue.md`).

## Why

Asked 8 Nov: the report should match the catalogue. Today it is built from facts, discovery and lineage directly,
leads with archetypes (how many kinds of Dockerfile cover the estate) and bases, and writes CSV tables with DDL. The
owner's questions are now the catalogue's: how the images stack up, how many layers, which parents, when each layer
was made, changed and published, which images are blocked on unmaintained intermediates, and each Dockerfile's
complexity, user, ports, permissions and provenance. A report built from the same file as the catalogue cannot
disagree with it, and shows what the catalogue holds.

## The design

The report reads `catalogue/dockerfiles.jsonl` (written first, by the same run) and nothing else for its pages.
Its pages:

- **Summary**: how many Dockerfiles, images and locations; how deep the chains go and how many layers they carry
  (distributions); how many intermediates are unmaintained, by reason, and how many images they block; how many
  final images run as root, bind a low port, or ship a toolchain; how many did not parse.
- **Hierarchy**: the trees from each root (an external base or the estate's own), every node a Dockerfile and its
  images, with own and total layers, created, last changed and last published, and an unmaintained mark with its
  reason; each node links to its Dockerfile's page.
- **Blocked**: the unmaintained intermediates, ordered by how many images each holds back, each with those images.
- **A page per Dockerfile**: its entry made readable: locations; the stage graph (which stage builds on which,
  which copies from which); every copy and its origin; user, ports and every ownership or mode change; per stage
  instructions, commands and layers; its images, each with its chain to the root and `blockedBy`.
- **Organisations**: the same summary, per organisation, linking into the rest.

Pages are the static HTML the report writes today (`Page`, `Html`, `Svg`, `Style` kept); the page-per-Dockerfile
limit and the linking follow the current report's.

## Questions

1. Archetypes: remove the archetype pages and the archetype count (k80, k95) from the report, or keep one page for
   them?
2. The report's CSV tables and their DDL: remove them, so every output is JSON (the catalogue, `archetypes.json` if
   kept, `passes.json`)?
3. The end-of-life list setting (`EOL_LIST`): the catalogue dropped end-of-life status; drop the setting and the
   bases page's status column too?
4. Any page missing from the list above?

## Tests, in order

To be written once the questions are answered.
