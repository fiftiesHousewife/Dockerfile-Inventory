# The CLI: plan

Status: planned 10 Nov (asked 10 Nov: "plan the cli; grill me on how it should behave"); questions answered 10 Nov.

## Why

The project publishes libraries and a CLI to Maven Central; a company pulls them through its Artifactory mirror and
may wrap the CLI in a Gradle plugin. Today there is no CLI: a stage runs as `./gradlew :<stage>:run` from a checkout or
from its own distribution's `bin/`, each a separate `...Main` reading the environment; `survey` runs every stage but
the converter; `inventory <stage>` lived only in the removed image's shell script.

## What exists to build on

- Every stage's `...Main.run(Map<String, String> env)`, configured wholly by environment variables, each refusing a
  bad value by name (`DiscoveryMain`, `FactsMain`, `LineageMain`, `CatalogueMain`, `ReportMain`, `RulesMain`,
  `ValidatorMain`; `ConverterMain` has only `main`).
- `SurveyMain`: `survey [--try] <organisation URL>...`, the stages in order into `out/<organisation>/`, stopping at the
  first failure with one line naming it.
- The tools module (corpus and fixture collectors, the JCR demo) is for contributors, not the CLI.

## Design, in slices

The answers below settle each part; each slice is built test-first and gated as AGENTS.md says.

1. **The command.** A `cli` module: `docker-inventory` with `--help`, `--version`, `<command> --help`, a command per
   stage calling its `...Main.run(env)`, `survey` (the converter added), exit codes 0 to 3 (answer 15), the settings
   each command reads listed once and checked for near misses. Every module built for and tested on Java 21 (the
   three unnamed variables in `rules` rewritten). The distribution zip (`bin/docker-inventory`) and the plain jar;
   their publication is the publication plan's.
   Tests: dispatch to a fake stage per command; each help text; an unknown command and an unknown flag, 2; a stage
   failing, 1 with its one line; `--version` from the build; the near-miss warning; the zip's launcher running
   `--version` (a test on the installed distribution).
2. **Logs.** SLF4J in every module but the three libraries, Log4j 2 bound in the distribution; `LOG_LEVEL`,
   `--quiet`, `--verbose`, `LOG_FORMAT=json`, `LOG4J_CONFIG` (answer 8). Every `System.out` and `System.err` line in
   the stages becomes a log call at its level. Tests: the level each setting gives; a JSON line's fields; no token or
   header in a debug line of an HTTP call.
3. **Settings and load.** The YAML file (`--settings`), environment over file, the `gentle` and `normal` presets
   (`--load`), the rate limits required for a command that calls GitHub or Artifactory (2 when missing), a whole-run
   rate limiter and the slow-down back-off in `http` (answers 6, 7, 12, 13). Tests: the file read and refused by name;
   precedence; each preset's numbers; a missing limit refused; the limiter's spacing on a fake clock; the rate halved
   when the last minute's median doubles, and on a 429 or 503 from Artifactory, and restored.
4. **Selection and confirm.** `--repository`, `--repository-name-contains`, the run's folder (answer 14); the first
   pass, the summary and its estimate, `Continue? [y/N]`, `--yes`, `--max-dockerfiles` (answers 9 to 11), exit 3.
   Tests: each selection on recorded GraphQL pages; a repository that does not exist, 2; the summary from a fake
   GitHub; the estimate from call counts and limits; no terminal without `--yes`, 2; over the ceiling, 3; the first
   pass reused by the scan (no tree listed twice).
5. **GitHub Apps.** The JWT and installation tokens, renewed before they expire; the installations listed once; per
   organisation the app or the token (answers 4, 5), recorded in discovery's output and shown in the report. Tests:
   the JWT's claims signed with a test key; a token renewed near its expiry; an organisation with the app, one with
   only the token, one with neither (1, naming it); the record in the output.

## Questions answered

1. The artifact (10 Nov: both): a distribution zip (`bin/inventory`, `lib/*.jar`) for people, and a plain jar whose
   POM declares its dependencies, for a Gradle plugin to resolve through the mirror. No fat jar.
2. The Java a user needs (10 Nov: "21 is fine"): 21, so every module the CLI holds is built for and tested on 21 as
   the libraries are. Measured: everything compiles with `--release 21` but three unnamed variables (`_`) in `rules`
   (`RemovedLater`, `ChangedAfterVolume`, `ChangedAfterCopy`).

3. The command (10 Nov: `docker-inventory`, not `inventory`; the commands: "yes that's fine"): `survey <organisation
   URL>...` (the stages in order, the converter added; `--try`, `--repository`, `--repository-name-contains`,
   `--max-dockerfiles`, `--yes`), one command per stage (`discovery`, `facts`, `lineage`, `catalogue`, `report`,
   `rules`, `validator`, `converter`), `--version`, `--help` (the commands), `<command> --help` (its settings: name,
   whether required, default, one line). A setting with a known prefix but no such name is warned of with the
   nearest real one. The contributors' tools stay Gradle tasks.

4. GitHub credentials (10 Nov: "we prefer apps but the path is slow to deploy them in each org"): a GitHub App
   (`GHE_APP_ID`, `GHE_APP_PRIVATE_KEY_FILE`: a JWT exchanged for hour-long installation tokens, renewed during a run)
   beside a token (`GHE_TOKEN`, `GHE_TOKEN_FILE`, a personal or functional ID's token).
5. Both at once (10 Nov: yes): per organisation, the app's installation where the app is installed
   (`GET /app/installations` at the start), the token elsewhere; an organisation neither reaches stops the run naming
   it; the output records which credential read each organisation, so the report shows the app's rollout.

6. GitHub's load (10 Nov: "yes all three"; then "i dont want a default, i DO want rate limiting, I want to be forced to
   specify it (or set the default SUPER conservative)"): `GHE_MAX_REQUESTS_PER_SECOND` is required: a command that
   calls GitHub refuses to start without it, naming the setting; it caps the whole run's calls. `CONCURRENCY` stays
   at 8 by default; when responses slow (the last minute's median twice the run's), the rate halves until they
   recover.

7. Artifactory's load (10 Nov: "yes same"): `ARTIFACTORY_MAX_REQUESTS_PER_SECOND` required whenever lineage runs;
   lineage's concurrency stays at 16; the rate halves when responses slow and on a 429 or 503.

8. Logs (10 Nov: "wouldnt it be better to use a logging library", then "log4j pls"): the code logs through the SLF4J
   API, so the plain jar depends on `slf4j-api` alone and a Gradle plugin's logs land in Gradle's; the distribution
   binds Log4j 2 (`log4j-slf4j2-impl`, `log4j-core`). `LOG_LEVEL` (`error`, `warn`, `info` by default, `debug`),
   `--quiet` and `--verbose`; `info` is today's progress and the confirm summary, `warn` what is skipped or refused,
   `debug` each HTTP call (method, path, status, time; never a header or a token), each wait, slow-down and retry, and
   each organisation's credential. One line per event with a timestamp and the level, or `LOG_FORMAT=json`
   (`JsonTemplateLayout`, one object per line); `LOG4J_CONFIG` names a configuration of one's own. Logs to stderr;
   stdout holds only the prompt and `--version`. The three libraries do not log.
9. A partial scan (asked 10 Nov by the owner; answered 10 Nov): on `survey` and `discovery`, `--repository
   owner/name` (repeatable; one the credential cannot read, or that does not exist, stops the run before any fetch)
   and `--repository-name-contains text` (repeatable, case-insensitive, any of them); together, the union; neither,
   the whole organisation. A selection writes to its own folder (`out/payments-billing/`, say).

10. A confirm step (asked 10 Nov by the owner; the proposal taken, 10 Nov): a first, read-only pass lists the
    selected repositories, their commits and trees (and, with lineage, the Artifactory repositories' images) under the
    same rate limits, reused by the scan; then one summary (repositories, those with Dockerfiles, Dockerfiles and
    distinct ones, the calls left to GitHub and Artifactory at their limits, the likely time, the output folder) and
    `Continue? [y/N]`; no leaves the first pass on disk; with no terminal, it refuses unless given `--yes`.
11. A ceiling on Dockerfiles (asked 10 Nov by the owner; 10 Nov: refuse): `--max-dockerfiles N` stops after the first
    pass when it finds more than N, naming both numbers and suggesting a narrower selection; nothing more is fetched,
    `--yes` or not. `--try` stays as it is.

12. Settings (10 Nov: "variables plus a settings file - yaml - can we look at presets? I dont want too many choices",
    then "yeah but also keep a simple cli"): an optional YAML file holding only the choices a person makes (GitHub's
    URL, its app or token file, its load; Artifactory's URL, token file and load, or none to skip lineage; the
    platform profile); a load preset, `gentle` (GitHub 2 calls a second, 2 repositories at once; Artifactory 5 and 4)
    or `normal` (GitHub 10 and 8; Artifactory 20 and 16), counts as setting the rate limit, exact numbers still
    allowed; everything else (timeouts, retries, proxy, CA bundle, link thresholds, footprints) keeps its default and
    its environment variable, in the README's advanced section. An environment variable overrides the file.

13. A simple command line (10 Nov: "sure"): no file needed for a first run (`GHE_TOKEN_FILE` and `survey <URL>
    --load gentle`); `survey`'s flags are `--load`, `--settings`, `--repository`, `--repository-name-contains`,
    `--max-dockerfiles`, `--try` and `--yes`; the other commands take `--settings` and, where they call a server,
    `--load`; everything else lives in the file or the environment.

14. Output (10 Nov: "yes keep that"): `./out/<run>/` under the working directory by default (`<run>` the organisation,
    `-try` for a try, the selection's text for a selection), one folder per stage, a rerun resuming into it; `--out`
    or `out:` in the file elsewhere; the confirm step names it; a survey's last line says where `index.html` is.

15. Exit status (10 Nov: "yes those codes are fine"): 0 finished; 1 a stage failed (the last log line names it and
    why); 2 a usage error before anything is fetched (an unknown command or flag, a bad or missing setting, a
    selection matching nothing, no terminal without `--yes`); 3 stopped by the user (no at the confirm step, or over
    `--max-dockerfiles`). The validator's findings never change the code: gating on them belongs to what wraps the
    CLI, reading `action-plan.json`.

## Raised by the owner (10 Nov): credentials, rate limits, the load on GitHub and Artifactory, log levels

What the stages do today:

- **Credentials.** GitHub: a bearer token from `GHE_TOKEN` or `GHE_TOKEN_FILE` (re-read when the file changes or
  after a 401), so a personal access token or a functional ID's token works; a GitHub App does not (no JWT, no
  installation token). Artifactory: a bearer token from `ARTIFACTORY_TOKEN` or `ARTIFACTORY_TOKEN_FILE`; no user and
  password or API key.
- **Rate limits.** GitHub: retries a 429 or a rate-limited 403 when the server says (`Retry-After`, or the reset
  time), up to `MAX_RATE_LIMIT_WAIT` (1 hour); with under a tenth of the limit left, spreads the calls left until the
  reset (`RatePacing`). Only when GitHub sends rate-limit headers: a GitHub Enterprise Server whose admin turned rate
  limiting off sends none, and nothing then slows the run. Artifactory: sends no rate-limit headers; a 429 or 503 is
  retried with backoff.
- **Load.** Bounded only by concurrency: discovery scans 8 repositories at once (`CONCURRENCY`), lineage reads 16
  images at once; no cap on requests per second, and no quiet hours.
- **Logs.** Each stage prints progress lines with `System.out` and errors with `System.err`; no levels, no
  timestamps, nothing machine-readable.

## Built, slice 1 (10 Nov)

Every module is compiled by the JDK 25 toolchain for `--release 21` and tested on Java 21 (`inventory.targetRelease`
in `inventory.java`, which the libraries' setting became); the three rules with unnamed patterns test with
`instanceof`. The survey runs the converter before the report (`CONVERTER_DIR` for it), through `ConverterMain.run`.
The `cli` module: `DockerInventory` dispatches to `Commands.ALL`, checks a stage's arguments (none) and its required
settings before it runs (2), turns a failure into one line (1), warns of a near-miss setting (`NearMisses`, within two
edits of a setting sharing its prefix), prints `--help`, `<command> --help` (`Help`) and `--version` (`Version`, the
build's `inventory.cliVersion` written into the jar). `installDist` makes `bin/docker-inventory`, whose `--version`
and exit status are tested on the installed distribution. Run by hand on the owner's survey's discovery: `facts`
reads it as `./gradlew :facts:run` did. Not yet: the stages still print with `System.out` (slice 2); the zip and the
plain jar's publication belong to the publication plan.

## Built, slice 2 (10 Nov)

Every stage logs through SLF4J (`slf4j-api`); the libraries do not log. The CLI binds Log4j 2 (`log4j-slf4j2-impl`,
`log4j-core`, `log4j-layout-template-json`): `log4j2.xml` writes to stderr, a timestamped line with the level or, with
`LOG_FORMAT=json`, Elastic's common schema one object a line; OpenRewrite and JGit at warn and above. `Logging` reads
`LOG_LEVEL`, `LOG_FORMAT`, `LOG4J_CONFIG`, `--quiet` and `--verbose` (anywhere on the command line) and sets them as
system properties before anything logs; a bad level or format exits 2. At debug, discovery's and lineage's senders log
each call, failure and wait (`HttpLog`: method, host and path; never the query or a header). The stages lost their
own launchers: `docker-inventory <stage>` is the one way to run one. The credential each organisation is read with is
logged in slice 5, when there is a choice.

Not test-first, owned: `Logging`'s tests were written beside it rather than run red first.

## Slice 3 in detail (10 Nov)

- `Throttle` (in `http`): calls spaced at most `requestsPerSecond` apart across every thread of a run; the run's normal
  latency is the median of its first 20 calls; when the last minute's median passes twice that, or a call answers 429
  or 503, the rate halves (to a sixteenth of the setting at least), and when the last minute's median is back under
  one and a half times normal it doubles back towards the setting; at most one change every 10 seconds. Discovery's
  and lineage's senders wait on it before each call and tell it each answer.
- Settings: `GHE_MAX_REQUESTS_PER_SECOND` and `GHE_CONCURRENCY` (discovery), `ARTIFACTORY_MAX_REQUESTS_PER_SECOND` and
  `ARTIFACTORY_CONCURRENCY` (lineage); `CONCURRENCY`, which one survey had to share between both, goes. A stage refuses
  to start without its rate (also when run as a library), naming it.
- `--load gentle|normal` (or `GHE_LOAD`, `ARTIFACTORY_LOAD`) fills in a rate and a concurrency left unset: gentle
  GitHub 2 and 2, Artifactory 5 and 4; normal GitHub 10 and 8, Artifactory 20 and 16.
- `--settings file.yaml`: `github` (`url`, `tokenFile`, `load`, `requestsPerSecond`, `concurrency`), `artifactory`
  (the same), `platform`, `out`; a token's value is refused there (a file, never the secret), as is an unknown key,
  each by name. The environment overrides the file; `survey` takes `github.url` when given no URL.

Tests, in order: `Throttle` (spacing on a fake clock, across threads; halving on a slow minute and on 429 and 503; the
floor; doubling back; one change in 10 seconds); `Load`; `SettingsFile`; the CLI's flags and the required rates (2,
naming them); the stages refusing without a rate; the senders waiting on the throttle.

## Built, slice 3 (10 Nov)

`Throttle` (`http`) spaces a run's calls to a server and slows with it, as designed above; discovery's and lineage's
senders wait on it and tell it each answer, and the stages read `GHE_MAX_REQUESTS_PER_SECOND` and
`ARTIFACTORY_MAX_REQUESTS_PER_SECOND` (`Throttle.rate`: required, refused by name) and `GHE_CONCURRENCY` and
`ARTIFACTORY_CONCURRENCY`. In the CLI, `Flags` takes `--settings` and `--load` from anywhere; `SettingsFile` reads the
YAML (a token's value refused, an unknown key refused with the keys there are); `Load` fills in each server's rate
and concurrency; the environment wins over the file; `survey` takes `github.url` when given no organisation. A command
that calls a server requires its rate before it runs (2), and the tokens are required unless their files are set
(`Setting.requiredWith`, `requiredUnless`, shown in the help). Tried with the installed command: a settings file with
no token gets as far as discovery's token check; without a load, 2 naming the setting; a token in the file, 2.

## Slice 4 in detail (10 Nov)

- `--repository` and `--repository-name-contains` (repeatable) become `REPOSITORIES` and `REPOSITORY_NAME_CONTAINS`
  (comma-separated), so a selection also works through the environment. Discovery's `Selection` keeps a repository
  named exactly (`owner/name`, any case) or whose name holds one of the strings (any case). A named repository the
  organisations' listing does not hold, or a selection that keeps nothing, stops discovery after the listing, before
  any tree is walked: `SelectionNotFound`, exit 2.
- The first pass is the listing and the tree walks (resumable, as now); then `FirstPass`: repositories selected, those
  holding Dockerfiles, locations, distinct Dockerfiles, and GitHub calls left (blobs, YAML and last changes, 50 a
  call). Over `MAX_DOCKERFILES` (`--max-dockerfiles`; distinct Dockerfiles), or declined, discovery stops: `RunStopped`,
  exit 3. A library caller confirms everything; the CLI asks.
- The estimate: GitHub's calls left at its rate, plus the later stages' seconds per distinct Dockerfile, measured on
  the combined estate. Artifactory is not estimated: lineage learns how many images there are by listing them.
- `--yes` skips the question; without a terminal and without `--yes`, `survey` and `discovery` exit 2 before running.
- A selection's run writes to `out/<organisation>-<text>` (`-<name>` for a repository, joined with `+`).

Tests, in order: `Selection`; discovery stopping on a missing name and on nothing kept; `FirstPass` and its calls left;
discovery asking after the first pass and stopping when declined or over the ceiling; the survey's folder for a
selection; the CLI's flags, its question and answer, `--yes`, no terminal, and the exit statuses 2 and 3.

## Built, slice 4 (10 Nov)

Discovery: `Selection` (`REPOSITORIES`, `REPOSITORY_NAME_CONTAINS`) keeps the listed repositories it names or whose
name holds a string, any case, and throws `SelectionNotFound` for a name not listed or a selection keeping nothing,
before any tree is walked. After the tree walks, `FirstPass` (repositories, those with Dockerfiles, locations, distinct
ones, GitHub calls left, the output folder) goes to a `Confirmation`; over `MAX_DOCKERFILES`, or declined, `RunStopped`,
with the first pass kept for a rerun. `DiscoveryMain.run(env, confirmation)` and `SurveyMain.run(args, env,
confirmation)`; the survey maps the two stops to 2 and 3 and names a selection's folder (`out/payments-billing`). The
CLI: `--repository`, `--repository-name-contains` (both repeatable), `--max-dockerfiles` and `--yes` become those
settings (and `ASSUME_YES`); `Prompt` prints the summary and the likely time (GitHub's calls left at its rate, 10 s,
and 14 ms per distinct Dockerfile, measured on the combined estate: facts 11 s, catalogue 2 s, rules 11 s, validator
1 s, converter 45 s, report 3 s for 5,250) and asks; without a terminal and without `--yes` it exits 2 before running.
Tried with the installed command: no terminal, 2; a bad ceiling, 2; the settings in `discovery --help`. Not tried
here: a real first pass and question against GitHub (no token in this session); the owner's run is the test.

