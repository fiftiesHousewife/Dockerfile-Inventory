# The survey

One command runs the whole survey: discovery, the facts, lineage (when `ARTIFACTORY_URL` is set), the catalogue,
the rules, the OpenShift validator, the converter and the report, each stage reading what the one before wrote. It
needs Java 21 or newer. Try it first:

```bash
export GHE_TOKEN_FILE=~/secrets/ghe-token          # or GHE_TOKEN=$(env -u GITHUB_TOKEN gh auth token)
docker-inventory survey --try https://github.example.com/acme      # 10 repositories with a Dockerfile
docker-inventory survey https://github.example.com/acme            # every repository
docker-inventory survey https://github.example.com/acme https://github.example.com/beta   # one estate
```

`docker-inventory` is the distribution's launcher (`./gradlew :cli:installDist` builds it into
`cli/build/install/docker-inventory/`; from a checkout, `./gradlew -q :cli:run --args="survey ..."` runs it too).
`docker-inventory --help` lists the commands: `survey`, and each stage alone (`discovery`, `facts`, `lineage`,
`catalogue`, `report`, `rules`, `validator`, `converter`); `docker-inventory <command> --help` lists the settings that
command reads, which are required and their defaults. A setting given with a near-miss name (`GHE_TOKN`) is warned of.
It exits 0 when done, 1 when a stage fails (its last line says which and why), and 2 on a usage error found before
anything runs (an unknown command or argument, a required setting left out); what the validator finds never changes
the exit status.

Logs go to stderr, one timestamped line an event: `LOG_LEVEL` (`error`, `warn`, `info` by default, `debug`) or
`--quiet` (errors only) and `--verbose` (debug: each call to GitHub or Artifactory, its status and time, and each wait;
never a token, a header or a query) set how much; `LOG_FORMAT=json` writes one JSON object a line (Elastic's common
schema) for a log collector; `LOG4J_CONFIG` names a Log4j 2 configuration of your own.

A try writes to `out/acme-try/`, the full run to `out/acme/`, several organisations to `out/acme+beta/` (`OUT_DIR`
overrides), one folder per stage; the last
line says where the report's `index.html` is. A try proves the token, the network and the proxy in a few minutes
and shows a real report; it stops once ten repositories holding a Dockerfile are found (a few more when they come in
the same batch). The URL is a GitHub Enterprise Server host with the organisation (`https://ghe.example.com/acme`),
`https://github.com/acme`, or `https://acme.ghe.com`. Every other setting below is passed on to the stage that
reads it, so the same environment configures a proxy, a CA bundle, Artifactory or a profile. A rerun of a full run
resumes discovery where it stopped; a stage that fails stops the survey with one line saying which and why (GitHub
refusing the token is explained).

Each stage also runs alone, to rerun one: see the other guides.
