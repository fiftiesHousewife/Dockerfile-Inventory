# Fixes from the code review of 6 Nov: plan

Status: R1-R3 built 6 Nov; R4 next.

A code review of the session's changes (`c0da31d^..HEAD`) found nine likely bugs and one rule breach, all from
reading the code. Each is fixed test-first: a failing test from the reviewer's scenario, then the fix.

| # | Problem | Fix | Module |
| --- | --- | --- | --- |
| R1 | Archetype cards copy whole Dockerfiles into the dashboard, so a secret-shaped `ENV`/`ARG` value would be written out (AGENTS.md forbids it) | Done 6 Nov (owner's choice): the example is shown with secret values hidden: on `ENV`/`ARG`/`LABEL` lines the value of a secret-shaped name, in either form, and any secret-shaped value anywhere. The name and shape rules moved from `rules` to `dsl.Secrets`, shared by S2 and the report | `report`, `dsl`, `rules` |
| R2 | A word in an exec form that is not valid JSON throws out of the converter and stops the whole run | Such an instruction becomes `Raw` ("exec form that is not valid JSON"), as Docker itself falls back to shell form | `converter` |
| R3 | `ARG A B` rewritten `ARG A=$A B=$B` loses a global default inside a stage | Done 6 Nov. The cause was deeper: rewrite-docker folds `ARG A=1 B=2` into one argument `A` = `1 B=2` (upstream issue 12), so facts had read every multi-value `ARG` wrongly, not only the retry's. Facts now reads an `ARG`'s names and values from its text, and a default that is only the ARG's own name counts as none | `facts` |
| R4 | The retry's rewrites touch quoted `ARG` values, continued lines and heredoc bodies | Rewrites apply only to an instruction's own lines outside heredoc bodies; `ARG A B` only when every word is a plain name and the line is not continued | `facts` |
| R5 | The template check can call a valid Dockerfile a template (`${{ github.sha }}` in a comment, Go's `{{ end }}`), and misses a template that parses only on the retry | Comment lines are skipped; `${{` and Go template keywords do not count; the check also runs after a retry succeeds | `facts` |
| R6 | Decoded newlines and tabs in an exec form are written raw, splitting the instruction | JSON strings escape control characters (`\n`, `\t`, `\u00XX`) | `dsl` |
| R7 | In the converter's `ENV`/`LABEL` reading, a backslash inside double quotes is always consumed (`"C:\tools"` becomes `C:tools`) | As in the shell and as in S2: only before `"`, `\`, `$` (and a newline) | `converter` |
| R8 | `\$` in the model means a literal dollar, but a literal backslash then a variable (`"pre\\$HOME"`) models the same | The model keeps a literal backslash as `\\` too; the renderer keeps `\\` and `\$` pairs and escapes any other backslash | `converter`, `dsl` |
| R9 | A digest from a build argument counts as pinned for the builders' R1 | R1 needs a literal digest; a build argument is a breach with its own message | `dsl` |
| R10 | Lineage's key reading treats any apostrophe as a quote, but the classic builder writes history values unquoted (`MSG=it's`) | A quote opens a value only at its start; the quote-aware key reading is shared by facts, rules and the converter (lineage keeps its own, held to facts by the contracts) | `lineage`, `facts`, `rules`, `converter` |
| R12 | The converter reads `ARG` through the same folded node: `ARG A=1 B=2` becomes one argument `A` = `1 B=2`, which renders with a different meaning (found while fixing R3) | Read the names and values from the text, as facts now does | `converter` |
| R11 | The tokeniser expands `$B` inside a single-quoted assignment (`A='$B'`), which the shell keeps literal | Single-quoted text is not expanded | `facts` |

Order: R1, R2 and R3 (a leak, a crash, wrong facts), then R4 to R11.

## Questions

1. R1: how the cards show their example Dockerfile. Answered 6 Nov: shown, with secret values hidden.
