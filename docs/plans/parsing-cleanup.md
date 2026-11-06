# Cleanup of the parsing code: plan

Status: planned 6 Nov; scope answered 6 Nov (facts and the shared helpers; lineage stays separate); steps 1 and 2 built (step 2: `RetryText`, `Templates` and `MergedLines` out of `FactsParser`; a comment line no longer opens a heredoc in the retry, as it already did not when blanking comments); step 3 built (`ShellWords`: characters to words and operators; `ShellCommands`: those to commands and assignments; `ShellTokeniser` the facade); step 4 built (`Expansions`: the one variable expander and command-substitution test, used by `FactExtractor`, `ShellCommands` and `PackageCalls`; streams for the directive check, package expansion and stage lookup; long lines wrapped), step 5 next.

The owner asked for the parsing code to be made idiomatic and consistent: small classes, well-named methods, streams
over loops. The code grew fix by fix across five public estates and a review (about fifty changes), and it shows:
`ShellTokeniser` (329 lines) is one state machine holding words, quotes, substitutions, `if`, `case`, loops and
functions; `FactsParser` (253) mixes parsing, the retry's rewrites and template detection; `FactExtractor` (353)
holds an `ARG` reader beside the facts; and the same jobs are done several ways in several modules: joining
continuation lines (facts' `Steps`, `FactExtractor`, the converter's `joined` and `withoutContinuations`), reading
quoted `key=value` pairs (facts' `Steps.keys` and `argPairs`, the converter's `Pairs`), and unquoting.

A refactor only: behaviour does not change. Every existing test stays green before and after each step; the
openshift and docker-library numbers are checked unchanged at the end. A decision pulled out into its own method
gets its own test, as AGENTS.md asks.

## Design

| Now | After | Module |
| --- | --- | --- |
| `ShellTokeniser`: one class | `ShellWords` (characters to words: quotes, escapes, substitutions, literal dollars), `ShellCommands` (words to commands: separators, keywords of `if`/`case`/loops/groups, functions, `for` lists), `ShellTokeniser` the small facade the rest already calls | `facts` |
| `FactsParser`: parse, retry rewrites, templates | `FactsParser` (parse, retry, verdict), `RetryText` (the rewrites, one well-named method each: comments, JSON commas, flag defaults, `ARG` names), `Templates` (Jinja and template markers) | `facts` |
| `FactExtractor.argPairs`, `Steps.keys`, the converter's `Pairs` | `KeyValuePairs` in `facts`: one quote-aware reader with two outputs, keys and values as written, used by `Steps`, `FactExtractor`, rules and the converter (which turns values into the model's form in one small method). Lineage keeps its own reader, held to facts by the contracts | `facts`, `converter`, `rules` |
| Continuation joining in four places | `Continuations` in `facts`: join lines, keeping a backslash that ends the last line | `facts`, `converter` |
| Index loops over lists, `StringBuilder` appends in loops | Streams where they read better; character scanners stay loops (a scanner that looks back and ahead reads worse as a stream), each in a small method with one job | all |

## Order

1. `Continuations` and `KeyValuePairs` (shared helpers first; the callers move one at a time).
2. `RetryText` and `Templates` out of `FactsParser`.
3. `ShellWords` and `ShellCommands` out of `ShellTokeniser`.
4. Streams over loops, names, and Javadoc where it says what rather than why, module by module.
5. AGENTS.md's layout, and the numbers rechecked on openshift and docker-library.

## Question

1. Scope: the parsing code in `facts`, plus the shared helpers the converter and rules use (proposed); or `facts`
   only; or also lineage's history normaliser (now kept separate on purpose, so the contracts compare two
   independent readers)?
