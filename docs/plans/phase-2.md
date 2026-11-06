# Phase 2 — Cluster and report: plan

Status: planned 6 Nov; questions answered. Slices 1 to 5 built 6 Nov. Slices are built test-first, as in the earlier phases.

Phase 2 produces the number that sets strategy: how many archetypes cover 80% and 95% of the estate. Roughly 300
or fewer at 95% means golden base images first and a small DSL; a long, diverse tail means recipes first and the
DSL for new work. Module: `report`. Inputs, all files: `facts.jsonl` (Phase 1), discovery's `manifest.jsonl`, and,
when present, lineage's `links.jsonl` and `effective.jsonl` (Phase 1b). Gate: the decision is written down and
agreed (the decision paper is written by people from these numbers, not by the pipeline).

## Answers (6 Nov)

| # | Question | Answer | What it changes |
| --- | --- | --- | --- |
| Q1 | How tables reach Oracle | Not decided | The report writes CSV tables and a DDL script; loading is left until the schema exists |
| Q2 | Dashboard | Both | A self-contained HTML page per run now, and the CSV tables for a dashboard tool later |
| Q3 | Unit of the estate | Each Dockerfile location | Every manifest row (repository and path) weighs one; a Dockerfile copied into 40 repositories weighs 40 |
| Q4 | End-of-life source | An internal list | Read from `EOL_LIST`, a CSV of `imagePath,tagPrefix,status,eolDate` (format assumed; say if yours differs) |

## Decisions taken without asking (say if any is wrong)

| # | Decision | Why |
| --- | --- | --- |
| D1 | Each pass's count is reported both as distinct groups and weighted by locations | Distinct groups show variety; locations show reach |
| D2 | Near-duplicates: MinHash with 128 hash functions over the final-stage package set plus base family, then LSH with 32 bands of 4 rows; candidate pairs are confirmed at an exact Jaccard of at least 0.8 and merged with union-find | The delivery plan's starting point; LSH keeps it near-linear at 20,000 files |
| D3 | A Dockerfile with no packages and the same base family as another collapses only through the earlier passes, not pass 4 | An empty set has no meaningful similarity |
| D4 | Effective user per Dockerfile comes from its newest linked image (Phase 1b, Q19); without lineage output, the user from facts is shown and the dashboard says so | Phase 2 can run before 1b has real data |
| D5 | Parse failures are counted in the estate, as their own archetype, so coverage is never overstated | They are part of what has to be migrated |

## Slices

1. **Module and inputs.** `report` module; `Estate.read(factsDir, discoveryDir, lineageDir?)` gives each
   Dockerfile (blob) with its facts, hashes and locations, and parse failures with theirs.
   1. Each blob's facts, hashes and signature are read from `facts.jsonl`.
   2. Each blob's locations come from `manifest.jsonl`; a blob listed there but in neither facts nor failures is
      reported as missing, not dropped.
   3. Parse failures are read with their locations (D5).
   4. Without a lineage directory, links and effective users are absent, not an error.

   As built: the fixtures are the facts stage's real output for three small Dockerfiles. Final-stage packages are
   the packages of package calls in the last stage, as the signature uses. `UserKind.of` applies the facts
   classification to an effective user (`1001:0` is numeric) and has its own table test, written after it.

2. **Exact passes.** `Passes.exact(estate)` gives the count after blob SHA, text-normal hash and signature hash,
   each as distinct groups and as locations (D1).
   5. Blob SHA: one group per blob.
   6. Text-normal: blobs with the same text hash form one group.
   7. Signature: groups by signature hash; parse failures form one group of their own (D5).

3. **Near-duplicates.** `NearDuplicates.of(groups)` merges signature groups whose package set plus base family
   are at least 0.8 similar (D2, D3).
   8. MinHash signatures of equal sets are equal; Jaccard estimate within 0.1 of exact for known sets.
   9. Two groups at Jaccard 0.8 or more merge; at less, they do not.
   10. Merging is transitive (A~B, B~C gives one group).
   11. Empty package sets never merge through this pass.
   12. Results do not depend on input order.

   As built: the first MinHash tests passed against an all-zero stub (zero agrees with zero), so they were
   strengthened with exact Jaccard values and a pair of sets that must differ. The coefficients come from a fixed
   seed and products are taken mod 2^61 - 1 with 128-bit multiplication. Tests 10 to 12 came after the code;
   comparing package-less groups, or keeping the input order, each made its test fail. A merged group takes
   the key, package set and family of its member at the most locations.

4. **Archetypes.** `Archetypes.rank(groups)`: groups sorted by locations, with k80 and k95.
   13. k80 is the fewest archetypes whose locations reach 80% of all locations; k95 likewise.
   14. Ties in size order by signature, so the ranking is stable.
   15. `archetypes.json`: each archetype's rank, locations, blobs, signature, a representative blob and its
       repositories.

   As built: coverage is compared in whole numbers (`covered * 100 >= percent * total`), not by rounding a
   floating-point share. The representative is the member at the most locations.

5. **Base catalogue.** `BaseCatalogue.of(estate, eolList)`: bases by locations, with family, version (major),
   digest-pinned share, and status from the internal list.
   16. Bases are grouped by image path and tag; locations summed.
   17. Pinned share: locations whose `FROM` carries a digest.
   18. End-of-life status from the list by image path and longest matching tag prefix; no match is `unknown`.
   19. Unresolved `FROM` (an `ARG` with no default) is its own row.

   As built: a tag prefix covers a tag only on a version boundary (`9` covers `9.4` and `9-1100`, not `91`). The
   image path follows Docker's host rule, as facts' `fromPath` does.

6. **Tables.** CSV files for Oracle and a DDL script (Q1): `dockerfiles.csv`, `locations.csv`, `archetypes.csv`,
   `bases.csv`, `images.csv` (from lineage when present). Values are escaped; nothing secret-shaped is written.
   20. Each table's header and one row per record; commas, quotes and newlines escaped.
   21. The DDL creates each table with the CSV's columns and types.

7. **Dashboard.** `dashboard.html`, one self-contained page (no external scripts): files found, unique blobs,
   unique text-normal, unique signatures, k80 and k95, parse-failure and opaque-`RUN` rates, bases by family and
   version, pinned versus floating, end-of-life share, package managers, root versus non-root final user
   (effective when lineage ran, D4), release-index coverage.
   22. Every number on the page comes from the same objects the tables are written from.
   23. Text is HTML-escaped.
   24. Without lineage output the user panel says it shows Dockerfile users, not effective ones.

8. **Run.** `ReportMain`: `FACTS_DIR`, `DISCOVERY_DIR`, `[LINEAGE_DIR]`, `[EOL_LIST]`, `OUT_DIR`. Writes
   `passes.json`, `archetypes.json`, the tables, the DDL and the dashboard. Untested, like the other mains.
