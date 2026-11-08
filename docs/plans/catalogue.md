# The Dockerfile catalogue: plan

Status: planned 8 Nov; questions 1-3 answered 8 Nov; it feeds the OpenShift validator (`openshift-validator.md`).

## Why

Asked 8 Nov: before anyone converts a Dockerfile to the DSL, they want to know what the estate's Dockerfiles say.
Today's outputs answer the estate's questions (how many archetypes, which bases, how many unparseable) but not
"what does this Dockerfile build": `dockerfiles.csv` has the base, the user's kind, two counts and the archetype,
and `facts.jsonl` holds more in a shape made for clustering. Consumers need one structured description per
Dockerfile they can load and report on: how deep it is, what it links to, its user, ports, entrypoint, packages.

## What each Dockerfile's record holds

- **Identity and place**: the blob SHA; every location (repository, path, scope, commit scanned); whether it parsed
  and why not; its archetype (rank, key) and whether it is a metadata-only bundle or a template.
- **Stages**: per stage its name, and its base (name, tag, digest; pinned or not; approved and end-of-life status
  from the lists the rules read) or the earlier stage it builds on; which stages copy from which (`COPY --from`).
- **The final image as written**: user as written and its kind (root, numeric, named, unset); exposed ports;
  entrypoint and command with their form; working directory; volumes; health check; shell; stop signal;
  environment variable names; labels; packages installed (manager, names, pinned, cleaned in the same `RUN`);
  BuildKit features used (heredocs, mounts, secrets).
- **Depth**: the layers the Dockerfile adds (each `RUN`, `COPY` and `ADD` of the final image, through earlier
  stages it builds on), instruction counts by keyword, `RUN`s the shell reader could not read.
- **Cross-links across the estate**: the Dockerfile that builds this one's base, those built on this one, and how
  many estate Dockerfiles deep its chain of bases is; from lineage, where a registry was read, the image's real
  layer count and its effective user, ports and environment with the image that set each.
- **Findings**: how many of each rule's findings it has (never a secret's value).

## Output

From `report`, which already joins facts, discovery and lineage, in a new folder `catalogue/`:
`dockerfiles.jsonl` (one record per Dockerfile, as above) and flat tables for the reporting database, with their
DDL: `dockerfiles.csv`, `locations.csv`, `stages.csv`, `ports.csv`, `packages.csv`, `environment.csv`,
`labels.csv`, `links.csv`. The README documents every field.

## Questions

1. One record per distinct Dockerfile, its locations listed (and a locations table), or one per location?
2. Cross-links when no registry has been read: only from lineage, or also inferred (a base's name matched to the
   image a Dockerfile's labels say it builds), marked as inferred?
3. Environment and label values: names only, or values with secret-shaped ones hidden as the report hides them?
4. In `report` as above, or a stage of its own?

Answers (8 Nov): 1, one record per distinct Dockerfile with its locations listed, and a locations table; 2,
links from lineage where a registry was read, and otherwise inferred from base names matched to the images other
Dockerfiles' labels say they build, each inferred link marked with its evidence; 3, environment and label values
shown, a secret-shaped value or one under a secret's name shown as `(hidden)` with its hash, by the same
test rule S2 and the report use. 4, in `report` as planned (answered with the validator's plan, which reads the facts and
lineage directly).

## Tests, in order

To be written once the questions are answered.
