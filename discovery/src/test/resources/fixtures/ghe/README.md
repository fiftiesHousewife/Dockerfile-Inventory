Hand-written in the shapes GitHub Enterprise Server documents for these endpoints. Replace each with a
recorded, trimmed response from the real server once the scanner token exists (BACKLOG.md).

`history.json` answers a query for three locations: the first has a last change, the second has no history at its
path, and the third's commit is not found (`object` null).

The REST files (`rest-*.json`) are for a credential GraphQL refuses, hand-written the same way from GitHub's REST
documentation: `rest-repositories-page1.json` and `rest-empty-page.json` are two pages of `/orgs/{org}/repos`;
`rest-branch.json` is `/repos/{repo}/branches/{branch}`; `rest-commits.json` is `/repos/{repo}/commits?path=...`
with one commit. The three `rest-blob-*.json` are `/repos/{repo}/git/blobs/{sha}`, their base64 wrapped at 60
characters as GitHub wraps it: a Dockerfile, a PNG header (a NUL in it, so binary) and a Dockerfile in Latin-1 (not
UTF-8). `graphql-rate-limit.json` answers the smallest query; `graphql-forbidden.json` (a 403's body) and
`graphql-forbidden-errors.json` (a 200 with errors) are the two ways a token may be refused GraphQL.
