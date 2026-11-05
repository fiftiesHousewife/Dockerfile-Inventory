# Dockerfile Inventory

Finds every Dockerfile in the GitHub Enterprise estate at its last-released commit, parses each unique
one with OpenRewrite's `rewrite-docker`, links images to their bases through Artifactory, and reports
how many archetypes cover the estate and which rules each Dockerfile breaks.

Status: Phase 0 (discovery) in progress. See [BACKLOG.md](BACKLOG.md).

## Build

Requires a JDK 21 (Gradle provisions one if none is installed).

```bash
./gradlew check
```

Contributor and agent notes are in [AGENTS.md](AGENTS.md).
