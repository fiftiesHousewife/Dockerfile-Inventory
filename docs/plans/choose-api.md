# Choosing discovery's API from the command line: plan

Status: planned 12 Nov; questions answered 12 Nov; done 12 Nov, tests 1-4: `ApiChoice` (`GHE_API`, with
`GHE_LAST_CHANGES` moved in), `--api`, `github.api`. Test 5, `GitHubLiveTest`, written and skipping without
`GH_TOKEN`; its first run waits on the owner, as this sandbox cannot reach GitHub.

## Why

Asked 12 Nov: discovery picks REST only for a credential GraphQL refuses (`rest-only.md`), so the REST path cannot be
tried against real GitHub with a token GraphQL accepts, such as `gh auth token`. The owner wants to decide it from the
command line, to run discovery over REST on github.com and compare its output with a GraphQL run.

## The design

- A setting `GHE_API`, read in `DiscoveryMain`: `auto` (the default: the probe decides, as today) or `rest` (every
  credential over REST, no probe). Anything else is refused naming the variable.
- A flag `--api <value>` in `cli`'s `Flags`, mapped onto `GHE_API` as `--repository` is onto `REPOSITORIES`, and
  `github.api` in the settings file.
- `ApiChoice(restOnly, lastChanges)`, read from the environment (`GHE_LAST_CHANGES` moves into it from
  `DiscoveryMain`), is what `GitHubAccess.client` and `chosen` take: `rest` returns `GitHubRest` without asking
  `graphqlAllowed`.
- A live test, `GitHubLiveTest`, tagged `github-live` and run by `./gradlew :discovery:githubLive`, outside `check`:
  with `GH_TOKEN` set (a token GraphQL accepts, such as `gh auth token`) it discovers one public repository
  (`LIVE_REPOSITORY`, default `sclorg/postgresql-container`) on github.com twice, `GHE_API=auto` and `GHE_API=rest`,
  both with last changes, and asserts the output files are the same: `blobs/` byte for byte, every `.jsonl` file line for
  line, sorted (repositories are scanned in parallel). Without `GH_TOKEN` it skips saying so. This is
  `rest-only.md`'s test 10.
- Listed in `SettingGroups.GITHUB` and in `docs/guide/discovery.md`, with the comparison as an example.

## Tests, in the order they will be written

1. `ApiChoice.restOnly(env)`: blank and `auto` false, `rest` true; anything else refused naming `GHE_API`.
2. `GitHubAccess.chosen` with `rest`: `GitHubRest`, and no GraphQL call made.
3. `Flags.of`: `--api rest` puts `GHE_API=rest` in the environment; `--api` without a value refused.
4. `SettingsFile`: `github.api` maps to `GHE_API`.
5. `GitHubLiveTest`, run by the owner (this sandbox cannot reach GitHub).

## What the answers settle

- **Values**: `auto` and `rest` only; a `graphql` that skips the probe has no use the probe does not cover.
- **The live test too**, asked alongside the flag.
