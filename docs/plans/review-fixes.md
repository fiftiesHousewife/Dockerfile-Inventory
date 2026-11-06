# Fixes from the code review of 6 Nov: plan

Status: all built 6 Nov.

A code review of the session's changes (`c0da31d^..HEAD`) found nine likely bugs and one rule breach, all from
reading the code. Each is fixed test-first: a failing test from the reviewer's scenario, then the fix.

| # | Problem | Fix | Module |
| --- | --- | --- | --- |
| R1 | Archetype cards copy whole Dockerfiles into the dashboard, so a secret-shaped `ENV`/`ARG` value would be written out (AGENTS.md forbids it) | Done 6 Nov (owner's choice): the example is shown with secret values hidden: on `ENV`/`ARG`/`LABEL` lines the value of a secret-shaped name, in either form, and any secret-shaped value anywhere. The name and shape rules moved from `rules` to `dsl.Secrets`, shared by S2 and the report | `report`, `dsl`, `rules` |
| R2 | A word in an exec form that is not valid JSON throws out of the converter and stops the whole run | Such an instruction becomes `Raw` ("exec form that is not valid JSON"), as Docker itself falls back to shell form | `converter` |
| R3 | `ARG A B` rewritten `ARG A=$A B=$B` loses a global default inside a stage | Done 6 Nov. The cause was deeper: rewrite-docker folds `ARG A=1 B=2` into one argument `A` = `1 B=2` (upstream issue 12), so facts had read every multi-value `ARG` wrongly, not only the retry's. Facts now reads an `ARG`'s names and values from its text, and a default that is only the ARG's own name counts as none | `facts` |
| R4 | The retry's rewrites touch quoted `ARG` values, continued lines and heredoc bodies | Done 6 Nov: as planned | `facts` |
| R5 | The template check can call a valid Dockerfile a template (`${{ github.sha }}` in a comment, Go's `{{ end }}`), and misses a template that parses only on the retry | Done 6 Nov: as planned | `facts` |
| R6 | Decoded newlines and tabs in an exec form are written raw, splitting the instruction | Done 6 Nov: as planned | `dsl` |
| R7 | In the converter's `ENV`/`LABEL` reading, a backslash inside double quotes is always consumed (`"C:\tools"` becomes `C:tools`) | Done 6 Nov, with R8 | `converter` |
| R8 | `\$` in the model means a literal dollar, but a literal backslash then a variable (`"pre\\$HOME"`) models the same | Done 6 Nov: a model value is written as inside double quotes (`\$` a dollar, `\\` a backslash); `Pairs` reads Docker's escapes into that form and the renderer keeps the pairs, doubling any other backslash | `converter`, `dsl` |
| R9 | A digest from a build argument counts as pinned for the builders' R1 | Done 6 Nov: as planned | `dsl` |
| R10 | Lineage's key reading treats any apostrophe as a quote, but the classic builder writes history values unquoted (`MSG=it's`) | Done 6 Nov: in lineage a quote opens only at the start of a key or value (a mid-word quote, valid but rare, now reads differently from facts; the contracts pass). Rules' copy of the key reader is gone: it uses facts' `Steps.keys`. The converter's reader returns values in the model's form, a different job, and lineage keeps its own, held to facts by the contracts | `lineage`, `rules` |
| R12 | The converter reads `ARG` through the same folded node: `ARG A=1 B=2` becomes one argument `A` = `1 B=2`, which renders with a different meaning (found while fixing R3) | Done 6 Nov: one model `ARG` per name, names from facts' reader, values in the model's form from `Pairs`; a stage's comments stay aligned | `converter` |
| R11 | The tokeniser expands `$B` inside a single-quoted assignment (`A='$B'`), which the shell keeps literal | Done 6 Nov: a single-quoted or escaped dollar is marked while a word is read, so only the shell's own references expand | `facts` |

Order: R1, R2 and R3 (a leak, a crash, wrong facts), then R4 to R11.

## Questions

1. R1: how the cards show their example Dockerfile. Answered 6 Nov: shown, with secret values hidden.

## As built

All twelve done 6 Nov. Rerunning openshift after them found two regressions, both fixed test-first: R4 had narrowed
the `ARG A B` rewrite so far that an `ARG` mixing names with simple values, or continued over lines, failed again
(2 files); and R12 read a continued `ARG`'s backslash as a name (4 conversions stopped rendering). After the fixes
openshift's numbers match those before the review (64 parse failures, 165 unreadable RUNs, 2,730 of 2,833 round
trips), with the review's bugs gone.

One commit (R3, `1f647ca`) was pushed with SpotBugs failing, because the command chained the commit after `check`
with `;`; the next commit fixed it, and every commit since is made only when `check` passes.

