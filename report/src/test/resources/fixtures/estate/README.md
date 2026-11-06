`facts.jsonl` and `parse-failures.jsonl` are the facts stage's real output for three small Dockerfiles (6 Nov),
so the report reads exactly what Phase 1 writes. `manifest.jsonl` places `blob-a` in two repositories and adds
`blob-gone`, which has no facts row, as a discovery location whose blob was never parsed.
