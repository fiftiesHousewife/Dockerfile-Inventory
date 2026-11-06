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

