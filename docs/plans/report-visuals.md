# A more visual report: plan

Status: built 6 Nov.

The owner chose four changes to the dashboard. It stays one self-contained file (Q2 of Phase 2): charts are inline
SVG written by the report module, hover is the SVG `<title>` element (no script), and every chart keeps its numbers
in a table beside it or in a `<details>` under it, so nothing is colour alone.

## What changes

1. **Headline health strip.** At the top, five or six tiles, each marked good, watch or act against thresholds,
   with the word as well as the colour (status colours from the dataviz reference: good `#0ca30c`, warning
   `#fab219`, critical `#d03b3b`).
2. **Archetype coverage curve.** Locations covered (y, 0-100%) against archetypes ranked (x), one 2px line, with
   the 80% and 95% points marked and labelled (`k80`, `k95`); hover on each point gives rank and share.
3. **Bases as bars.** The top 20 bases as horizontal bars (length = locations), coloured by status (end of life,
   supported, unknown), with the pinned-by-digest part of each bar textured; labels and counts as text.
4. **Stacked bars** for package managers, final user and parse outcome (parsed, template, failed), one bar each,
   segments in a fixed categorical order with 2px gaps, a legend with counts and shares.

Colours are CSS custom properties with their own dark-mode values (the reference palette's dark steps), not an
automatic flip.

## Design

- `Charts` (new, package-private): pure functions from numbers to SVG strings: `coverage(Ranking)`,
  `bars(List<Bar>)`, `stacked(List<Segment>)`. No layout outside them.
- `Health` (new): the strip's metrics and their thresholds, each metric a record (name, value, good-below,
  act-above, or the reverse), so the thresholds live in one place with tests.
- `Dashboard` calls them; the existing tables stay, under each chart in `<details>`.

## Tests, in order

1. Each health metric is good, watch or act by its thresholds, and the tile names the state in words.
2. The coverage curve's points follow the ranking's cumulative share; `k80` and `k95` sit at their ranks.
3. A bar's length is proportional to its locations; its status sets its class; its pinned share is textured.
4. A stacked bar's segments are proportional, in fixed order, with a legend giving each count and share.
5. The page holds no external URL, and text from the estate is escaped inside SVG too.

## Questions

1. The health strip's metrics and thresholds. Answered: six, good / act, watch between: parse failures <1% / >5%;
   final user root <10% / >30%; bases pinned by digest >80% / <20%; bases at end of life 0% / >5%; scanned at a
   released commit >90% / <50%; RUN too complex <5% / >15%.

## As built

As planned, with: the parse outcome's counts are worked out from the summary (distinct files from the blob pass,
failures from the rate of non-template files); package managers keep a fixed order (dnf, microdnf, yum, apt-get,
apt, apk, zypper, then the rest alphabetically) and only those present appear; final user always shows its four
kinds. Test 5 passed first time: the charts already escaped every label.

## Slice 2: archetype cards (6 Nov)

The owner found the top-20 archetype table meaningless: each row was a raw signature. It becomes cards for the top
10 (the full ranking stays in `archetypes.json`), each with:

- a plain sentence built from the signature (`base:major|managers|packages|user|ports|entrypoint|stages`, the format
  `NormalForms.signature` documents) and the representative Dockerfile's base as written: "Builds on X, installs N
  packages with yum (a, b, c and 17 more), runs as a numeric user, exposes 8080 and 8443, starts with an exec-form
  command, one stage";
- its size: Dockerfiles, locations and repositories;
- one example: the representative Dockerfile's location and its full text, read from discovery's `blobs/`, in a
  collapsible section, escaped.

Tests, in order: 1. the sentence for each user kind, entrypoint form, no packages, many packages and several
stages; 2. the templates and parse-failure groups get their own sentence; 3. a card's numbers, repositories and
example come from the ranking, the estate and the blob; a missing blob leaves the example out; 4. the dashboard
shows ten cards with the example escaped inside `<details>`, and no signature table.

As built (slice 2): `ArchetypeCards` builds the sentence and finds each card's example; the heading is "Largest
archetypes" (a count in it read oddly with fewer than ten). A card names five repositories and counts the rest.

## Slice 3: making the page explain itself (6 Nov)

The owner found several fields unclear. Built test-first:

- An info marker beside every tile, health metric and section heading; its text (in `About`) shows on hover and on
  keyboard focus.
- "Collapse passes" became "How many really different Dockerfiles?", its rows in plain words (different files;
  still different once comments and spacing are ignored; build a different kind of image; archetypes).
- When no base matched an end-of-life list, the bases section says so and how to give one (`EOL_LIST`), instead of
  showing every base as unknown without explanation.
- The cards' example Dockerfiles hide secret values (review fix R1, `review-fixes.md`).

