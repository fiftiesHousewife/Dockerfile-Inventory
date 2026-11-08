# The Dockerfile catalogue: plan

Status: planned 8 Nov; questions answered 8 Nov; first of the two to build (the OpenShift validator,
`openshift-validator.md`, reads the same extended facts).

## Why

Asked 8 Nov: before anyone converts a Dockerfile to the DSL, they want to know what the estate's Dockerfiles say.
Today's outputs answer the estate's questions (how many archetypes, which bases, how many unparseable) but not
"what does this Dockerfile build": `dockerfiles.csv` has the base, the user's kind, two counts and the archetype,
and `facts.jsonl` holds more in a shape made for clustering. Consumers need one structured description per
Dockerfile they can load and report on: how deep it is, what it links to, its user, ports, entrypoint, packages.

## What each Dockerfile's record holds

- **Identity and place**: the blob SHA; every location (repository, path, scope, commit scanned); whether it parsed
  and, if not, why; its archetype (rank, key); whether it is a metadata-only bundle or a template.
- **Stages**: per stage its name, and its base (name, tag, digest; pinned or not; approved and end-of-life status
  from the lists the rules read) or the earlier stage it builds on; which stages copy from which (`COPY --from`).
- **The final image as written**: the user as written and its kind (root, numeric, named, unset, unresolved);
  exposed ports; entrypoint and command with their form; working directory; volumes; health check (its options and
  command, or `NONE`); shell; stop signal; environment variables and labels with their values; packages installed
  (manager, names, pinned or not, cleaned in the same `RUN` or not); BuildKit features used (heredocs, mounts,
  secrets). A value that is secret-shaped, or stands under a secret's name, is `(hidden)` with its SHA-256, by the
  test rule S2 and the report use; nothing secret is ever written.
- **Depth**: the layers the Dockerfile adds to its final image (each `RUN`, `COPY` and `ADD` of the final stage
  and of the earlier stages it builds on by name), its instructions counted by keyword, the `RUN`s the shell reader
  could not read.
- **Cross-links across the estate**: the Dockerfile that builds this one's base, the Dockerfiles built on this
  one, and how many estate Dockerfiles deep its chain of bases is. Each link says where it comes from: `lineage`
  (a registry was read and the image linked to its Dockerfile) or `inferred`, with its evidence: the base's path
  (registry and tag aside) equal to the image another Dockerfile names in its `name`,
  `org.opencontainers.image.ref.name` or `io.k8s.display-name` label, or a final stage built on an earlier stage of
  another Dockerfile in the same repository. Where lineage exists, also the image's real layer count and its
  effective user, ports and environment, with the image that set each.
- **Findings**: how many findings of each rule it has, when the rules' output is given (`RULES_DIR`, optional).

## Where the data comes from

The report reads files only and imports nothing that reads Dockerfiles, so the facts stage provides what the
catalogue shows. `facts.jsonl` today has the stages' bases, packages, the user's kind, ports, the entrypoint's form
and the final image's steps; it gains the final image's configuration: working directory, volumes, health check,
shell, stop signal, entrypoint and command as written, environment variables and labels with values (secret ones
hidden as above, which makes `facts` depend on `dsl` for `Secrets`; `dsl` depends on nothing), the stage graph,
and the layer count. A change to `facts.jsonl` moves the corpus ratchet's expectations in the same commit.

## Output

From `report`, which already joins facts, discovery and lineage: `catalogue/dockerfiles.jsonl`, one JSON object per
Dockerfile, as above, and nothing else (asked 8 Nov: JSON only; how consumers load it is theirs to decide). The
README documents every field.

## Questions

1. One record per distinct Dockerfile, its locations listed (and a locations table), or one per location?
2. Cross-links when no registry has been read: only from lineage, or also inferred (a base's name matched to the
   image a Dockerfile's labels say it builds), marked as inferred?
3. Environment and label values: names only, or values with secret-shaped ones hidden as the report hides them?
4. In `report` as above, or a stage of its own?

Answers (8 Nov): 1, one record per distinct Dockerfile with its locations listed, and a locations table; 2, links
from lineage where a registry was read, and otherwise inferred, each inferred link marked with its evidence; 3,
values shown, a secret-shaped value or one under a secret's name `(hidden)` with its hash; 4, in `report` (answered
with the validator's plan, whose stage reads the facts and lineage directly).

Progress: step 1 done 8 Nov (`FinalConfig`, `StageGraph`, both on each facts row; `facts` depends on `dsl`).

## Tests, in order

One assertion each, several in `assertAll`; Dockerfile fixtures are real files, trimmed.

1. `facts`: the final image's configuration (working directory, volumes, health check, shell, stop signal,
   entrypoint and command), one field at a time, from real fixtures; environment and label values with a secret
   hidden; the stage graph; the layer count through stages built on by name. The corpus ratchet updated.
2. `report`: a catalogue entry from one fact line and its locations; a Dockerfile that did not parse as an entry
   with its locations and why; secret values never written (the secret-shape guard over each line too).
3. Links: from lineage's `links.jsonl`; inferred from the `name` and `org.opencontainers.image.ref.name` labels,
   each with its evidence; depth along the chain; a cycle of inferred links not followed.
4. `ReportRun` writing `catalogue/dockerfiles.jsonl`; the README's field list.
5. Measured on the combined estate: every distinct Dockerfile has an entry, parsed or not, and the counts agree with
   `facts/rates.json`.
