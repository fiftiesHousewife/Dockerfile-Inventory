# One report over every source: plan

Status: built 7 Nov.

The owner asked for the report to combine every Dockerfile source discovered so far. Each stage reads one discovery
directory, so the sources are merged before facts rather than the stages taught to read several:
`scripts/combine.sh <out> <discovery>...` concatenates `manifest.jsonl` and `repos.jsonl` (identical rows once) and
stores each blob once; it warns when a repository comes from more than one source, as it would then count twice.

Sources (7 Nov): sclorg (`out/demo4`), openshift as rediscovered for the OKD run (`out/okd`: the same organisation,
26 repositories at their released commits; the older `out/openshift` would count openshift twice), konflux-ci,
redhat-developer, docker-library, bitnami; lineage from the OKD run.

| | Combined |
| --- | --- |
| Locations, repositories, files | 8,199, 1,453, 5,250 (62 files shared across organisations) |
| Parse failures, templates left out | 35 (0.7%); 53 templates |
| Unreadable RUNs | 81 of 13,222 (0.6%); 96 in PowerShell apart |
| Round trip | 5,154 of 5,250 |
| Archetypes | 2,087; 514 cover 80% of locations |

Dashboard: https://claude.ai/artifact/Npbva376tdsXBuHsaTko2B
