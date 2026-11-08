# The report as a view of the catalogue: plan

Status: planned 8 Nov; questions answered 8 Nov; built 8 to 9 Nov. Follows the catalogue (`catalogue.md`).

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
  final images run as root, bind a low port, or ship a toolchain; the footprints (micro, minimal, full, distroless,
  scratch, alpine) of the images, by their chain's root; the images over the layer limit; the images with each
  language runtime (Java, Go, Node.js, Python) in their hierarchy, in the final image or in a build stage only;
  the unusual steps, counted by kind; how many did not parse.
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

Answers (8 Nov): 1, one archetypes page (the families and their sizes), linked from the summary, not leading it;
2, the CSV tables and their DDL go; 3, end-of-life leaves the report (no `EOL_LIST`, no share in the health strip, no
column on a bases page; rule R2 keeps its own list); 4, the pages as listed. Asked after: collections are JSON Lines,
one record per line, as the catalogue is, so `archetypes.json` becomes `archetypes.jsonl`; a single summary object
(`passes.json`) stays JSON.

## Tests, in order

One assertion each, several in `assertAll`; the report's pages read only `catalogue/dockerfiles.jsonl`, and the
first slices remove before they add, each keeping what stays byte for byte.

1. Removal: the CSV tables and `ddl.sql` no longer written (`Tables`, `Table` and their tests go); `archetypes.jsonl`,
   one archetype per line, in place of `archetypes.json`. Done 8 Nov. End-of-life is woven through the pages being
   replaced (the health strip, the figures, the bases page), so it leaves with them in step 8.
2. `CatalogueView`: the catalogue file read back into entries the pages use (counts, chains, blocked, per
   organisation), with no other input. Done 8 Nov.
3. Summary page from the view: counts, chain depth and layer distributions, unmaintained by reason and what they
   block, root / low-port / toolchain shares, footprints, runtimes, unusual steps, unparsed. Built 8 Nov (`OverviewPage`), written by step 8.
4. Hierarchy page: the trees from each root, each node linking to its Dockerfile's page. Built 8 Nov.
5. Blocked page: unmaintained intermediates ordered by how many images each holds back. Built 8 Nov.
6. A page per Dockerfile: locations, stage graph, copies, permissions, per-stage counts, images with their chains. Built 8 Nov (`DockerfilePage`, `DockerfileSections`).
7. Organisations: the summary per organisation; the archetypes page linked from the summary. Built 9 Nov: each archetype links to one of its Dockerfiles' pages, which lists its near-duplicates, in place of a page per archetype.
8. `ReportRun` writing the pages from the catalogue, the old pages and `EOL_LIST` removed with what only they used;
   README; measured on an estate. Done 9 Nov.

## Measured (9 Nov)

On stored outputs, the facts written with the catalogue's fields and OKD's lineage analysis rerun offline (23 weak
links, as in `lineage-from-data.md`):

- OKD: 2,646 catalogue entries, 2,646 Dockerfile pages, 1 second; 18 registry images credited, 10 Dockerfiles with
  candidates; 3 trees, 8 Dockerfiles on another; 39,891 links, none broken.
- The combined estate (`out/all`, no lineage): 5,250 pages in 3 seconds; 104,096 links, none broken.
- Read on the way: the layer bands sorted as text, an image credited to identical Dockerfiles counted once per
  Dockerfile, an unset user shown as "null"; each fixed with a test.
