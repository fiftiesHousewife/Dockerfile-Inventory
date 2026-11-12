# Discovery over REST alone: plan

Status: planned 11 Nov; questions answered 11 Nov; done 11 Nov, tests 1-8: `GitHubRest` (`RestRepositories`,
`RestBlobs`, `RestHistory`) chosen per credential by `GitHubRequests.graphqlAllowed`, the head commit asked for
only outside the release index (`GitHub.headCommit`), the confirm step's calls counted each way (`GitHub.calls`),
`GHE_LAST_CHANGES` (`github.lastChanges`). Test 9 dropped: the report never counts last changes towards
"unmaintained" (that view reads the registry and archived repositories), so a run without them only leaves the
Dockerfile pages' "last changed" blank. Test 10, the measurement, is `./gradlew :discovery:githubLive`
(`choose-api.md`) and waits on the owner's run: this sandbox cannot reach GitHub, so the REST fixtures are written in
GitHub's documented shapes, as the GraphQL ones are.

## Why

Asked 11 Nov: the owner's personal access token cannot use GitHub's GraphQL API, and discovery needs it for three of
its five calls. Today `GitHubHttp` lists organisations and reads trees over REST, but lists an organisation's
repositories, fetches blobs (50 a query) and finds each location's last change (50 a query) over GraphQL. With such
a token the first GraphQL call fails and the run stops.

## The design

A second way to talk to GitHub, REST only, behind the same `GitHub` interface, so `Scans`, `RepoScan`, `BlobFetch`,
`ChangeFetch` and everything after them are unchanged and the output files are the same.

| Call | GraphQL today | REST |
| --- | --- | --- |
| An organisation's repositories | `organization.repositories`, 100 a page | `GET /orgs/{org}/repos?per_page=100&type=all`, 100 a page (`full_name`, `default_branch`, `archived`, `fork`, `pushed_at`) |
| The default branch's head commit | in the same page (`defaultBranchRef.target.oid`) | `GET /repos/{repo}/commits/{branch}` with `Accept: application/vnd.github.sha` (40 bytes back), only for a repository the release index does not name |
| A blob's text | 50 a query, GitHub's `text` (truncated past a size, `isBinary` said) | `GET /repos/{repo}/git/blobs/{sha}`: base64 content; binary when it is not UTF-8 or holds a NUL; never truncated |
| A location's last change | 50 a query (`history(first: 1, path:)`) | `GET /repos/{repo}/commits?sha={commit}&path={path}&per_page=1` |

The cost: REST makes one call per blob and per location where GraphQL makes one per 50, so a run makes many more
calls: one per distinct blob, one per location and one per repository outside the release index, where GraphQL
makes a fiftieth of the first two. On the 15,000-location estate that is over 15,000 calls for the last changes
alone; at GitHub's 5,000 an hour for a token, several hours, with the throttle and rate-limit waits that already
exist doing the pacing. The confirm step's estimate counts calls by the chosen API, so the owner sees
this before saying yes.

## Tests, in the order they will be written

Each against recorded REST responses in `discovery/src/test/resources/fixtures/`, served by the loopback server the
GraphQL tests use.

1. `RestRepositories.repository(node)`: a repository from one element of `/orgs/{org}/repos`.
2. `RestRepositories.nextPage(link)`: the next page from the `Link` header, none on the last.
3. `RestBlobs.blob(sha, node)`: text from base64 content; binary for a NUL or bytes that are not UTF-8.
4. `RestHistory.lastChange(request, page)`: the commit and date from the first element, none for an empty page.
5. `GitHubRest` end to end on the loopback server: repositories (two pages), a head commit, blobs, last changes.
6-10. As settled below.

## What the answers settle

- **Found out, not set**: at the start of a run each credential is probed once with the smallest GraphQL query
  (`{ rateLimit { remaining } }`, which any token, App or not, may ask). A refusal (401, 403, or a reply whose
  `errors` say the token may not use GraphQL) sends that credential's organisations over REST; a network failure or
  a 5xx is not a refusal, and stops the run as today. The log and the confirm step name the API each organisation is
  read over.
- **Last changes off by default over REST**: a REST organisation's locations get no last change unless
  `GHE_LAST_CHANGES=true` (`github.lastChanges: true`) asks for them; GraphQL organisations get them as today. The
  report's unmaintained view says, for an organisation without them, that its last changes were not read, rather
  than counting its Dockerfiles as maintained.
- **Per credential**: a run mixing an App (GraphQL) and a token (REST) reads each organisation the fastest way its
  credential allows; `PerOrganisation` holds the API beside the credential.

Tests 6 to 10:

6. `Pending.calls` by each organisation's API, last changes counted only where they are read.
7. `ApiProbe`: GraphQL for a reply with data; REST for a 401, a 403 and a GraphQL `errors` reply; a 5xx thrown.
8. `GHE_LAST_CHANGES`: `true` or `false`, anything else refused naming the variable.
9. The unmaintained view for an organisation whose last changes were not read.
10. Measured: discovery over REST (with last changes) and over GraphQL on a public organisation, outputs compared
    byte for byte.
