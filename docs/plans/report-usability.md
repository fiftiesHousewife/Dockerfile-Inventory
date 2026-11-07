# A report that is easier to use: plan

Status: built 7 Nov.

The owner asked for the report to be easier to use, to highlight patterns that apply to individual estates (here,
each GitHub organisation within a combined report), to make detail a click away rather than on the page, and found
the archetypes and the lineage section hard to parse. An independent review against the dashboard of all six
organisations (8,199 locations) agreed, and found more. Its findings, ranked:

1. One repository skews every estate number unremarked: `konflux-ci/community-operators-prod` holds 3,021 of 8,199
   locations, nearly all operator bundles (`FROM scratch`, metadata only), which make archetype 1 and push "final user
   root" to 77.6% (about 62% without them; bitnami 9%, konflux-ci 96%).
2. The health strip claims what the data cannot support: "bases at end of life 0.0%" is *good* with no end-of-life
   list; "scanned at a released commit 1.0%" is *act* where there is no release index. Both are "no data".
3. The same numbers appear three or four times (strip, tiles, passes table, rates table) before the first chart.
4. Archetype cards are generated sentences led by what an archetype does not do; two of the top ten are not
   archetypes (templates, an unresolved `${BASEIMAGE}`); repository lists give no proportions.
5. Lineage is a flat list of 34 registry paths; "weak" is never explained; the actionable items (3 outdated bases,
   4 gaps) are not marked.
6. Charts misfit: `scratch` and `unresolved` plotted as bases; package managers stacked although locations count
   twice; enum names (`ROOT`) as labels.
7. No route to detail: the CSVs and `archetypes.json` are not linked.

## Design

Separate generated pages (question 1), each small and self-contained (inline CSS and SVG, relative links), no
JavaScript:

```
report/index.html            the summary: no Dockerfile text, no image paths
report/orgs/<org>.html       one per organisation: the same summary, filtered
report/archetypes.html       every archetype ranked -> archetypes/<rank>.html
report/bases.html            every base by family
report/lineage.html          the tree, gaps, outdated bases
```

| # | Change | Where |
| --- | --- | --- |
| U1 | Health strip: a value and a word (good, watch, act, no data) with an icon; under each cell the organisation that contributes most | summary, each organisation |
| U2 | Organisations compared: a table with inline bars (locations, repositories, files, % root, % pinned, top base family, largest repository's share), each name linking to its page; small multiples of final user per organisation | summary |
| U3 | "Applies to": a sentence per organisation where its rate crosses the strip's thresholds, differs from the estate by more than 20 points, or it alone uses a base family | summary, each organisation |
| U4 | Archetypes as a ranked table: a short name from the signature (base family + manager + user; "bundle" and "template" named), locations with a bar, files, organisation chips with counts, the largest repository's share; templates and unresolved bases out of the ranking, counted in a note | summary (top 10), archetypes page |
| U5 | Archetype detail page: the signature as a table, organisations and repositories with counts, every file with its locations, the representative Dockerfile folded with secrets hidden | archetypes/<rank>.html |
| U6 | Lineage on the summary: a strong / weak / none bar with what each means, and two action lists (outdated bases: image, base, newer push; gaps); the tree on its own page, grouped by root, `<details>` per subtree, outdated and gaps marked in place | summary, lineage page |
| U7 | Charts: bases by family without `scratch` and `unresolved` (a note), coloured by end-of-life status only with a list; package managers as plain bars; parse outcome and the passes as one line each; human labels and plurals | all |
| U8 | Removed from the summary: the tiles, the passes table, the rates table, the example Dockerfiles, the tree | summary |
| U9 | Downloads: links to the CSVs, `archetypes.json` and the DDL | summary |
| U10 | Metadata-only bundles (`FROM scratch`, no `RUN`, no entrypoint or command) counted apart, like templates: their own line with the organisations holding them, out of the user, base and archetype figures | all |

Built after the review's report split (done), test-first per section; the dashboard's charts follow the dataviz
rules already used (one axis, colour by job, a legend for two or more series, text in text colours).

## Questions

1. Pages: separate generated pages with links (shareable URLs, a small summary), or one self-contained file with
   sections shown on demand (keeps the README's "one page" promise, a larger file)?
2. Operator bundles (`FROM scratch` images holding only metadata, no `RUN`, no entrypoint): count them apart, as the
   report counts templates (their own line, out of the user, base and archetype figures), or keep them in every
   figure and only name them?

Answers (7 Nov): 1, linked pages; 2, bundles counted apart.

## Tests, in order

1. A metadata-only bundle is recognised and counted apart; a `FROM scratch` image with a binary and an entrypoint is not.
2. The strip says "no data" without an end-of-life list or a release index.
3. Per-organisation figures from locations and facts (the six organisations' real numbers as the fixture).
4. "Applies to" sentences from thresholds and differences.
5. Archetype names from signatures, including bundle and template.
6. Each page written, linked from the summary, and links resolving.
7. Lineage action lists from `outdated-bases.jsonl` and gaps.
8. The combined dashboard regenerated and published; the README's example links updated.

## As built (7 Nov)

Linked pages replace `dashboard.html`: `index.html`, `orgs/<org>.html`, `archetypes.html` (paged at 250) and
`archetypes/<rank>.html`, `bases.html`, `lineage.html`; each self-contained, no scripts. The data files
(`archetypes.json`, `passes.json`, `tables/*.csv`, `ddl.sql`) are byte-identical.

On the combined estate: 8,199 locations; bundles 3,234 locations (konflux-ci 3,021, openshift 153, redhat-developer
60); with bundles apart, final user root 62.9% (bitnami 9.0%, sclorg 24.4%, konflux-ci 46.9%, redhat-developer 54.8%,
openshift 72.4%, docker-library 94.3%); pinned 6.3% (konflux-ci 37.7%). The archetype pages rank 2,039 archetypes,
1,108 covering 80%; the top ten, named from what they build, cover 13.5%.

Decisions taken in building, left open by the plan:

- A bundle is a file whose final base is `scratch`, with no `RUN` in any stage, no entrypoint and no `CMD` in the
  final stage; bundles leave archetypes file by file, so an archetype keeps its files that do start something.
- The archetype pages rank after leaving out bundles, templates, unreadable files and bases set by a variable; their
  80% count (1,108) therefore differs from `passes.json`'s k80 (514), and the page says why. Detail pages are written
  for archetypes with two or more locations (546 on the combined estate); detail file names keep `archetypes.json`'s
  rank.
- "No data": end of life when no base in the estate matches a list; released when no location in the estate is
  released. The contributing organisation is the one with most locations on the wrong side.
- "Applies to": a strip state unlike the estate's, a share 20 points from it, a single repository holding half the
  locations, or base families no other organisation uses.
- No end-of-life colours in charts (the review's advice); a base's status shows in words when a list matched.
- Lineage only on the estate's pages (organisation pages have none); tree nodes do not link to archetype pages, as
  lineage gives locations, not archetypes.
- Dropped from the pages: the count of `RUN`s in another shell and of locations never parsed (both still in
  `rates.json`).

