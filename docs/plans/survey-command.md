# One command for a survey: plan

Status: planned 9 Nov; the questions answered 9 Nov; built and tried end to end 9 Nov.

## Why

Asked 9 Nov: how do people use this? Today a survey is up to eight stages run one by one (`discovery`, `facts`,
`lineage`, `catalogue`, `rules`, `validator`, `report`, `converter`), each told through the environment where the
previous one wrote. Only discovery has a helper, and it is a shell script. The owner wants it simple: a test run and a
full run for an organisation.

## Design

- `inventory survey <organisation URL>` runs every stage in order into `out/<organisation>/` and ends by printing
  where the report is. A small Java entry point (no shell scripts), reusing each stage's `...Main.run(env)`.
- The token from `GHE_TOKEN_FILE` or `GHE_TOKEN`; lineage only when `ARTIFACTORY_URL` is set.
- `--try` (asked 9 Nov): the same stages on a few repositories, end to end, so a real report shows in minutes and
  the token, network and proxy are proven.
- The per-stage commands stay, for the pod and for rerunning one stage.
- A try (asked 9 Nov) covers 10 repositories that hold a Dockerfile: discovery reads `TRY_REPOSITORIES` and scans
  the organisation's repositories in batches of its concurrency until that many held one, so a try finds at least
  10 (up to a batch more). Its output goes to `out/<organisation>-try/`, the full run's to `out/<organisation>/`
  (`OUT_DIR` overrides).
- The order: discovery, facts, lineage (with `ARTIFACTORY_URL`), catalogue, rules, validator (reading lineage, the
  catalogue and the rules), report (reading the validator). The converter stays out: it serves the migration, and
  the report runs without it (decided by default).
- Each stage's `...Main` gains a public `run(env)`, so the survey runs them in one process with the settings it
  computes; their `main` reads the environment and calls it.
- A new `survey` module, depending on every stage it runs; `inventory survey` in the image.

## Tests, in order

1. `Discovery` with a try limit: stops once enough scanned repositories held a Dockerfile; `DiscoveryMain` reads
   `TRY_REPOSITORIES`, refusing a value that is not a positive whole number.
2. `run(env)` on discovery, facts, rules and report (lineage, catalogue and validator have it).
3. `Survey.stages`: the stages in order with each one's settings, for a full run and a try, with and without
   `ARTIFACTORY_URL`; the organisation's folder name from each URL form.
4. `SurveyMain`: the arguments (`<URL>`, `--try`), refusing anything else with the usage.
5. Tried end to end: a try on a public organisation, run by the owner with their GitHub login.

## Questions answered

1. A try runs every stage on a few repositories end to end (9 Nov).
2. Ten repositories that hold a Dockerfile (9 Nov).

## Built (9 Nov)

Tests 1 to 4 as listed, each seen to fail first. `scripts/inventory` tried on a stub distribution (`inventory
survey --try <URL>` reaches the survey's start script; no arguments print the usage). `./gradlew :survey:run` runs
from the repository root, so `out/` is the repository's. Not yet tried: a survey against a real organisation, which
needs the owner's GitHub token (test 5).

## Tried (9 Nov)

The owner's first try failed at discovery with GitHub's 401: a `GITHUB_TOKEN` set elsewhere had been taken by
`gh auth token` instead of the login. The survey now stops a failing stage with one line saying which and why, and
explains a 401; the README gives `env -u GITHUB_TOKEN gh auth token`. The second try, on `https://github.com/openshift`:
862 repositories listed, 32 scanned until 10 held a Dockerfile (38 Dockerfiles, all parsed), every stage in order,
the report 44 pages and 398 links, none broken; under `restricted-v2` 30 of 38 images break, 5 drift, 3 run; 157
actions in 122 fixes.

