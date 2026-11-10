# Running inside a corporate network

Discovery and lineage read these from the environment as well; every one has a default.

| Setting | Default | What it does |
| --- | --- | --- |
| `HTTPS_PROXY`, `HTTP_PROXY` (either case) | none: direct | The outbound proxy, as `http://proxy:8080`; a user and password in the URL (`http://user:pass@proxy:8080`) answer the proxy's challenge and are never printed |
| `NO_PROXY` | none | Hosts that go direct: names, `.domain` suffixes, addresses, or `*` |
| `TRUST_BUNDLE` | the JDK's trust store | A PEM file of extra CA certificates (a corporate CA, a TLS-inspecting proxy's), trusted beside the JDK's own; on OpenShift, the injected `tls-ca-bundle.pem` |
| `GHE_TOKEN_FILE`, `ARTIFACTORY_TOKEN_FILE` | none | Read the token from a file instead of the variable (set one or the other); the file is read again whenever it changes and after a 401, so a token rotated by a mounted secret or a Vault agent is picked up mid-run |
| `CONNECT_TIMEOUT`, `HTTP_TIMEOUT` | `PT10S`, `PT60S` | ISO-8601 durations |
| `HTTP_RETRIES` | `5` | Attempts for a call that fails on the network or with a 502, 503 or 504, with growing waits (1 s, 2 s, 4 s ... up to 60 s) |
| `MAX_RATE_LIMIT_WAIT` | `PT1H` | The longest wait for a rate limit to reset; a longer one stops the run with the reset time, and a rerun resumes. With under a tenth of GitHub's limit left, discovery also slows its calls to last until the reset |

Each stage prints the settings it uses, without secrets, before it starts. A bad value stops it, naming the
variable. A GitHub organisation that enforces SAML single sign-on stops discovery at once with the link to authorise
the token; a repository or image Artifactory refuses is recorded as refused (`REFUSED` in lineage's rates) and the
rest is read.
