# Running inside a corporate network: plan

Status: planned 7 Nov; questions answered 7 Nov. Built 7 Nov: the `http` module, wired into discovery and lineage
(C1-C6, C8, C9 and the waits of C7). C10 built: `INVENTORY_MIRROR` (and `_USER`, `_PASSWORD`) replaces Maven Central and the plugin portal in both settings
files, checked against a real mirror. Next: rate pacing (the rest of C7), the pod image (C11).
Originally: the `http` module is built first; wiring it into the clients follows the code review's branches are merged (`quality-review.md`), as
the HTTP classes are being split there.

The owner asked what will fail inside a corporate network, and for everything to be configurable with sensible
defaults. Every public run so far went from a laptop straight to github.com and quay.io. The real runs go from a pod
on OpenShift to GitHub Enterprise and Artifactory, through whatever the network puts in between.

## What fails today

| # | Where | What happens | Why |
| --- | --- | --- | --- |
| C1 | Proxy | Calls to anything behind an outbound proxy fail (`UnknownHostException`, connection refused) | `java.net.http` ignores `HTTPS_PROXY`, `HTTP_PROXY` and `NO_PROXY`, which a corporate shell and OpenShift's cluster-wide proxy set; only JVM system properties work (why the sandbox needs `gradlew-claude`) |
| C2 | Proxy authentication | A proxy that asks for a user and password refuses every call | No `Authenticator`; Basic on `CONNECT` is disabled by the JDK unless `jdk.http.auth.tunneling.disabledSchemes` is cleared |
| C3 | TLS | `PKIX path building failed` against an internal GHE or Artifactory, or through a TLS-inspecting proxy | Only the JDK's own trust store is used; a corporate CA can only be added by changing the JVM |
| C4 | Token lifetime | A run longer than the token lives fails part-way: every later call is 401 | Tokens are read once from the environment; GitHub App installation tokens last an hour, Vault and OpenShift-mounted secrets rotate |
| C5 | SSO | A personal token not authorised for an organisation's SAML SSO gets 403 on every repository, reported as a failure per repository | The 403 body's SSO hint is not read; the run should stop early with the reason |
| C6 | Transient network errors | A reset connection, a proxy's 502 or 504, or a timeout fails that repository or image | GitHub calls retry only rate limits; Artifactory only 429 and 503; I/O errors are never retried |
| C7 | Rate limits | A primary limit can stall up to an hour; GHES admins can lower limits or disable them; GraphQL has its own point budget; secondary limits trip on concurrency | Waits are honoured but attempts (`MAX_ATTEMPTS`), timeouts and the longest wait are fixed in code; no pacing before the limit is hit; concurrency defaults to 16 |
| C8 | Restricted access | A repository or organisation the token cannot read, an Artifactory repository it may not list, or a disabled `/v2/_catalog` | Discovery records the repository as FAILED with the message (good); Artifactory's 403 is not told apart from other errors, and a refused catalogue ends the inventory |
| C9 | Timeouts | Large organisations' GraphQL pages or slow Artifactory manifests time out | One fixed timeout per module |
| C10 | Build | `./gradlew` cannot reach Maven Central, the Gradle plugin portal or the JDK download service (foojay); `verifyUpToDate` fails without the internet | Repositories are hard-coded; no mirror setting |
| C11 | The pod | `HOME` is not writable for an arbitrary UID; the JVM sizes its heap from the node, not the pod's limit; runs need Gradle and the internet at start | Not yet packaged as an image; runs are `./gradlew :x:run` |

## Design (defaults in brackets)

A new `http` module (the owner's choice, question 5) holds one tested piece per concern, used by both clients
(discovery's GitHub client, lineage's Artifactory client); it needs neither Jackson nor OpenRewrite.

| # | Setting | Behaviour |
| --- | --- | --- |
| C1 | `HTTPS_PROXY`, `HTTP_PROXY`, `NO_PROXY` (or lower case) [unset: direct] | A `ProxySelector` built from them: `NO_PROXY` holds hosts, `.suffix` domains, `*`, and IPs; JVM proxy properties still win when set |
| C2 | user and password in the proxy URL (`http://user:pass@proxy:8080`) | An `Authenticator` answering only the proxy's challenge; Basic allowed for `CONNECT`; never logged |
| C3 | `TRUST_BUNDLE` [unset: the JDK's store] (question 2: a PEM bundle) | PEM certificates added to the JDK's defaults (not replacing them); on OpenShift, the injected `tls-ca-bundle.pem` |
| C4 | `GHE_TOKEN_FILE`, `ARTIFACTORY_TOKEN_FILE` [unset: the variables as now] | The file is read again before each batch and after any 401, then the call is retried once; a file and a variable both set is an error (one source) |
| C5 | — | A 403 whose body or `X-GitHub-SSO` header names SSO stops discovery at once with that reason, before scanning thousands of repositories |
| C6 | `HTTP_RETRIES` [5] | Retries I/O errors, timeouts, 502, 503 and 504 with exponential backoff and jitter (1 s, 2 s, 4 s ... capped at 60 s); rate-limit waits as now |
| C7 | `MAX_RATE_LIMIT_WAIT` [PT1H] (question 3: wait up to it, then fail with the reset time; a rerun resumes), `CONCURRENCY` [8], `REQUESTS_PER_SECOND` [unset: no cap] | A wait longer than the maximum fails the call rather than stall; when `X-RateLimit-Remaining` falls under 10% of the limit, calls are spread over the time to reset; GraphQL's `rateLimit { remaining resetAt }` read the same way |
| C8 | — | Artifactory 401 and 403 recorded per repository as a skip with the reason (like discovery's FAILED), the run continues; the report's rates list them. A disabled catalogue falls back to the storage API's folder listing |
| C9 | `HTTP_TIMEOUT` [PT60S], `CONNECT_TIMEOUT` [PT10S] | ISO-8601 durations, both clients |
| C10 | Gradle properties `inventory.mavenMirror` and `inventory.pluginMirror` [unset: Maven Central and the portal] | One internal Artifactory remote in place of both; `org.gradle.java.installations.auto-download=false` documented for a network that blocks foojay; `verifyUpToDate` reads the same mirror and is skipped offline as now |
| C11 | `JAVA_OPTS` [`-XX:MaxRAMPercentage=75`] | A `Containerfile` on UBI 9 minimal: a jlink'd JRE and each stage's distribution (`installDist`), `inventory <stage>` as entrypoint, `HOME` and OpenRewrite's cache under `OUT_DIR`, group 0 writable for an arbitrary UID; built with Buildah, pushed nowhere |

Every setting is validated at start (a malformed proxy URL, an unreadable bundle, a bad duration stops the run with
the setting's name), and the run prints the effective settings, without secrets, before it starts.

## Questions

1. Tokens (C4): re-read a token file before each batch and on every 401 (works with OpenShift secrets, Vault agent
   and `gh auth token` refreshers alike); or also build GitHub App authentication in (app ID and private key ->
   installation tokens, refreshed hourly); or keep environment variables only?
2. TLS (C3): add a PEM bundle to the JDK's trust store, or rely on the platform (a JVM image built with the
   corporate CA) and only document it?
3. Rate limits (C7): fail a call whose wait would exceed `MAX_RATE_LIMIT_WAIT` (one hour by default), or always wait
   however long GitHub says?
4. The pod (C11): package the stages as one runnable image now (a Containerfile on UBI with a jlink'd JRE, `HOME` and
   caches under the writable `OUT_DIR`, heap from the pod limit), or keep `./gradlew` runs and document the pod's
   needs?
5. Shared HTTP code: a new `http` module both clients use, or a copy in each module (two small classes, kept the
   same by tests)?

Answers (7 Nov): 1, token files re-read; 2, a PEM bundle; 3, wait up to a maximum; 4, an image now; 5, a new
`http` module.

## Tests, in order

1. `ProxySettings`: `HTTPS_PROXY` with and without credentials, `NO_PROXY` hosts, suffixes, `*`, IPs, JVM
   properties winning, malformed URLs refused with the setting's name.
2. `TrustBundle`: a PEM with one corporate CA added to the defaults; a public site still trusted; a bad file refused.
3. Retries: an `IOException`, a 502 and a 504 retried with backoff; a 404 and a 403 not; attempts capped.
4. Rate pacing: low `X-RateLimit-Remaining` spreads calls; a wait over the maximum fails with the reset time.
5. SSO: GitHub's SSO 403 (recorded body and header, trimmed) stops discovery with the reason.
6. Artifactory: a 403 on one repository skips it with the reason and the inventory goes on; a 404 catalogue falls
   back to the storage listing (recorded fixtures).
7. Tokens: a token file read again after a 401 and the call retried once; a rotated file picked up before the next
   batch; a file and a variable both set refused.
8. Gradle mirrors: `build-logic` tests that the mirror properties replace the repositories.
9. Settings printed at start, no secrets.
10. The image: built with Buildah, each stage's `--help` runs as an arbitrary UID with a read-only root and a
    writable `OUT_DIR` (by the owner, as the sandbox has no Docker).
