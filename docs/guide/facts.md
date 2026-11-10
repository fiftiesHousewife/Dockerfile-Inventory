# Facts: reading each Dockerfile

```bash
export DISCOVERY_DIR=/data/inventory     # discovery's OUT_DIR
export OUT_DIR=/data/facts
docker-inventory facts
```

Each distinct Dockerfile is parsed once; a rerun parses only new ones.

| File | Contents |
| --- | --- |
| `facts.jsonl` | One line per Dockerfile: base image, package-manager calls per stage, final user and whether it is root, numeric or named, exposed ports, entrypoint form, BuildKit features, `RUN` counts, two hashes for grouping (cosmetic and functional duplicates), and the final image's instructions and base image path, which lineage matches built images against |
| `parse-failures.jsonl` | Dockerfiles the parser rejected, with the reason; a template saved under a Dockerfile's name (Jinja and similar) says so |
| `workloads.jsonl` | One line per workload in the YAML discovery fetched (`Deployment`, `StatefulSet`, `DaemonSet`, `ReplicaSet`, `Job`, `CronJob`, `Pod`, `DeploymentConfig`): its kind, name and namespace, its pod's host access and security context, each container's image, ports, probes, mounts and security context, and every location of its file |
| `yaml-rates.json` | The YAML files read, those holding workloads, the workloads, and those left unread: templates (`{{ }}`, `{% %}`) and text that is not YAML |
| `rates.json` | Parse-failure rate (templates left out and counted apart), the share of `RUN`s too complex to read, `RUN`s in another shell, unresolved `FROM` per organisation, BuildKit feature use |

The shell in each `RUN` is read for its commands: command substitutions (`$(uname -m)`), groups and subshells,
`if` and `case` (every branch counts), loops (read once; `for p in a b` gives `$p` the list) and functions (their
bodies). A `RUN` is too complex to read only when it pipes a download into a shell or uses rarer forms; its packages
are then not counted. A `RUN` under `SHELL ["powershell", ...]` (or `pwsh`, `cmd`) is counted apart as another
shell. The Dockerfile parser is the project's own fork of OpenRewrite's, with the bugs found on real estates fixed
(`docs/upstream/rewrite-docker-issues.md`); a file it still rejects is listed in `parse-failures.jsonl` with the line
and the reason.
