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

## Reshaped around the hierarchy (asked 8 Nov)

Seeing a first entry, the owner named what matters most: navigating the hierarchy and counting layers, identifying
parents, knowing when each layer was created, when its Dockerfile last changed and when it was last published as an
image, and finding the images blocked on unmaintained intermediates. The fields above that serve none of these
(shell, stop signal, instruction counts, the bundle flag) stay in the entry but no longer lead it; a base's
end-of-life status leaves it (asked 8 Nov: not useful).

What each answer needs, and where it comes from:

| Question | Data | Source today |
| --- | --- | --- |
| parents, children, path to the root, depth | each image's parent; each image's Dockerfile | lineage (`lineage.jsonl`, `links.jsonl`); without a registry, the inferred links already built |
| layers: own, inherited, total | the image's layers, and its parent's | lineage (`configs/`: `rootfs`, `history`); without a registry, the layers the Dockerfile declares |
| when each layer was created | each layer's `created` time and the step that made it | lineage (`configs/`: `history`) |
| when the Dockerfile last changed | the file's last commit before the scanned one | not collected: discovery has the scanned commit and the repository's last push, not the file's history |
| when it was last published | the image's last push | lineage (`images.jsonl`: `lastPushed`) |
| blocked on an unmaintained intermediate | per image, each ancestor that is unmaintained, and so every image under it | lineage (`outdated-bases.jsonl`), plus a definition of unmaintained (question below) |

The entry gains a `hierarchy` part: the chain from this Dockerfile's image to the root (each step its image, its
Dockerfile when the estate has one, its own layers, its created and last-published times, whether it is
unmaintained and why), the layer counts, and `blockedBy`: the unmaintained ancestors that hold this image back.

Questions (8 Nov):

5. What makes an intermediate unmaintained: not published for a time (how long), a parent published since it was
   last built (it has not been rebuilt on its updated base), its repository archived, its base end of life, or
   several of these?
6. When a Dockerfile last changed: worth discovery fetching each Dockerfile's last commit date (one more request
   per repository, through GraphQL), or is the repository's last push enough for now?
7. The unit: one entry per Dockerfile with its images' hierarchy attached (as now), or one entry per image, each
   naming its Dockerfile?

Answers (8 Nov): the archetype leaves the entry (it stays in the report and `archetypes.json`); 5, unmaintained is
any of: not rebuilt on its updated base (its parent published after it was built), not published for longer than
a threshold (default 12 months, a setting), its repository archived; 6, discovery fetches each Dockerfile's last
commit date up to the scanned commit (GraphQL, batched per repository, kept by blob); 7, one entry per Dockerfile,
the images it built attached, each with its chain to the root, layers, times and `blockedBy`, so an estate read
without a registry still gets every Dockerfile's entry.

**Also asked 8 Nov: complexity, permissions and provenance.** Per stage, so a multi-stage build reads as a graph:

- **complexity**: the stage graph (which stage builds on which, which copies from which), and per stage its
  instructions, `RUN`s, the commands in them, the `RUN`s the shell reader could not read, and the layers it adds;
- **where things come from**: every `COPY` and `ADD`, with its sources, destination and origin (the build context,
  an earlier stage, another image, a URL or Git repository), and its `--chown` and `--chmod`;
- **permissions**: the user and its kind, the ports, and every change of ownership or mode (`chown`, `chgrp`,
  `chmod` in `RUN`s, with their paths; `--chown` and `--chmod` on copies), so group-0 writability can be read off;
- facts records these per stage (`StageGraph` grows; the shell reader already splits `RUN`s into commands).

**Also asked 8 Nov: footprint.** Each image's footprint, the class of the base at the root of its chain: `micro`
(`ubi-micro`), `minimal` (`ubi-minimal`, `fedora-minimal`, `busybox`), `full` (`ubi`, `debian`, `ubuntu`, `fedora`,
`centos`), `distroless`, `scratch`, `alpine`, `slim`, or `unknown`; a Dockerfile built on another estate image
takes its root's class through the hierarchy (on the combined estate 2,119 of 4,516 runnable Dockerfiles are built
on such an image, so their own `FROM` says nothing). The mapping from base path to class ships as a settable list,
read as the approved-bases list is. Measured 8 Nov on the combined estate by final `FROM` alone: micro 47, minimal
641, full 729, distroless 171, scratch 150 (bundles aside), alpine 211.

**Also asked 8 Nov: unusual steps, too many layers, language runtimes.**

- **Unusual steps**: every step that is neither the everyday instructions (`FROM`, `RUN`, `COPY`, `ENV`, `ARG`,
  `LABEL`, `WORKDIR`, `USER`, `EXPOSE`, `ENTRYPOINT`, `CMD`) nor their everyday forms: `ADD` (above all of a URL or
  Git repository), `ONBUILD`, `SHELL`, `HEALTHCHECK`, `VOLUME`, `STOPSIGNAL`, `RUN` in exec form, with a heredoc or
  with `--mount`, `--network` or `--security`, `COPY --link`, `--parents` or `--exclude`, and a `SHELL` that is not
  `sh`. Each listed with its stage and form, so the catalogue can be searched for them and the report can count
  them; facts adds each stage's instructions by keyword and form.
- **Too many layers**: an image's total layers (from the registry's config where lineage read it, else the layers
  its chain's Dockerfiles declare), its own and its ancestors' share, flagged above a limit (`LAYER_LIMIT`, default
  40, as a setting; overlay storage refuses 128).
- **Language runtimes in the hierarchy**: Java, Go, Node.js and Python (a settable list of each runtime's evidence),
  found in a base's name (`openjdk`, `eclipse-temurin`, `ubi9/openjdk-21`, `golang`, `go-toolset`, `node`,
  `nodejs-20`, `python`, `python-311`), in packages installed (`java-*-openjdk*`, `golang`, `nodejs`, `python3*`) or
  in the environment (`JAVA_HOME`, `GOPATH`, `NODE_VERSION`, `PYTHON_VERSION`). Each runtime says where it is: in the
  final image, only in a build stage (a Go builder's compiler does not reach the image), or inherited from an
  ancestor, named with the evidence, so "every image with Java in its hierarchy" is one search.

**Also asked 8 Nov: what drives action** (all of these, not vulnerabilities):

| Field | Why | Source |
| --- | --- | --- |
| blast radius: images and repositories descending from it | fix-once value | catalogue hierarchy |
| release-scoped or default branch only | fix what ships first | discovery (scope) |
| converts and round-trips | a fix can be generated and checked, not hand-edited | the converter's `roundtrip.jsonl` |
| near-duplicate siblings | candidates for one shared base or template | the report's near-duplicate pass |
| rebuild cadence: how often published, and how long after its base | the slow links | lineage (`images.jsonl`, pushes over time) |
| image and layer sizes | biggest savings first | lineage (the manifests carry each layer's size) |
| base lag: its base tag against the newest tag of that base | concrete upgrades | the registry's tag list, read by lineage |
| owner: `CODEOWNERS`, or the repository's team | who acts | new: discovery fetches `CODEOWNERS` with the Dockerfiles |
| used: pulls in the last 90 days | an unused image is not worth fixing | new: Artifactory's download statistics, read by lineage |
| provenance: signed, an SBOM attached, built by a known pipeline | trust and release readiness | new: the registry's signatures and attestations; Konflux labels |
| architectures | multi-arch readiness | the registry's image indexes, read by lineage |
| where it runs: internet-facing, privileges asked for | exposure | the deployment manifests, a later slice (as the validator plan) |
| labels: the Dockerfile's (in the entry today) and the image's, which add what its base and its build set | provenance, ownership and versions as the images carry them | lineage (`configs/`: `config.Labels`); later (asked 8 Nov) |

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

Progress (8 Nov): facts records the final image's configuration and, per stage, its counts, copies, permission
changes and unusual steps; the report writes `catalogue/dockerfiles.jsonl` (no archetype, no end-of-life status),
each entry with its stages, packages, language runtimes (final, build-only, or inherited from the nearest ancestor
whose final image has it, with evidence), links (lineage or inferred) and footprint (its chain root's base class,
built in for now; the settable list to come), its hierarchy, each location's last change and its images' labels (slices
below). Still to build: the action fields.

Measured 8 Nov on the OKD estate (facts rerun, lineage of 35 tags): 2,646 entries for 2,646 distinct blobs (67
unparsed); 30 Dockerfiles carry a registry image, the other 2,549 count declared layers (median 2, 90th
percentile 6, three over 40); 156 sit only in archived repositories; image labels by source: build 389, unknown 210,
base 150, dockerfile 58, ancestor 27 (OKD's build adds `com.redhat.component`, `io.openshift.build.*`, `name`,
`version` to every image; its Dockerfiles set almost none); footprint unknown for 1,284 (the settable list's case);
no last changes, as that discovery output predates them.

## Slice: the hierarchy (planned 8 Nov; built 8 Nov)

From what lineage and discovery already write; the Dockerfile's last change (new discovery data) is the next slice.

- `RegistryImages` reads the lineage directory into one record per image: its names (`images.jsonl`), parent
  (`lineage.jsonl`), Dockerfiles (`links.jsonl`), layer count and created time (`configs/`: `rootfs.diff_ids`,
  `created`), newest push, and the newer base it was not rebuilt on (`outdated-bases.jsonl`).
- `Unmaintained` gives an image's reasons: not rebuilt on its updated base; not published for `STALE_AFTER_MONTHS`
  (default 12) before the newest push the registry holds (not the clock, so a rerun on the same output writes the
  same file); and a Dockerfile's: every repository holding it archived (`repos.jsonl`).
- `Hierarchy` per entry: each image built from it with its chain to the root (per step the image, its Dockerfiles,
  own layers, created, last pushed, reasons) and `blockedBy` (the unmaintained steps above it); layers own,
  inherited and total, counted from the newest image when one exists, else declared (the Dockerfile's own layers,
  and its inferred estate ancestors' as inherited), flagged above `LAYER_LIMIT` (default 40).
- `ReportMain` reads the two settings, refusing a value that is not a positive whole number, naming it.

Tests, in order (one assertion each, several in `assertAll`; the lineage fixture is the demo run's output, its
configs added unedited):

1. `RegistryImages`: an image's names, parent, Dockerfiles, layers, created and last push; the newer base from an
   outdated row.
2. `Unmaintained`: not rebuilt on its updated base; stale against the newest push; recent is not stale; a
   Dockerfile whose repositories are all archived; one archived location of two is not.
3. `Hierarchy`: an image's chain to the root with each step's own layers; `blockedBy` naming an unmaintained step;
   layers from the image; declared layers through inferred ancestors; over the limit.
4. `CatalogueRun` writing `hierarchy` with lineage and without; `ReportMain`'s settings and refusals; README.

## Slice: each Dockerfile's last change (planned 8 Nov; built 8 Nov)

Answer 6 above: discovery fetches, per location, the last commit that touched the Dockerfile's path up to the
scanned commit. A date belongs to a location, not a blob (one blob in two repositories changed at different
times), so it is kept by repository, scanned commit and path, and the catalogue shows it on each location.

- After the scans and the blob fetch, discovery asks for every location without a recorded change, 50 to a GraphQL
  query across repositories (as blobs are): per repository and commit an aliased `repository { object(oid) { ... on
  Commit { history(first: 1, path) { nodes { oid committedDate } } } } }`, one `history` per path.
- Each answer is appended to `changes.jsonl` (`repository`, `commitSha`, `path`, `changedIn`, `changedAt`) as its
  batch returns, so a killed run resumes; a location GitHub has no answer for is asked again on the next run.
- The report's catalogue reads `changes.jsonl` beside the manifest: each location gains `lastChanged` and
  `lastChangedIn`.

Fixtures are hand-written in the shape GitHub documents, as discovery's others are until a scanner token records
real ones.

Tests, in order:

1. `GraphQlHistory.query`: one aliased field per repository and commit, one `history` per path, names quoted.
2. `GraphQlHistory.read`: each request's change; a request without an answer left out.
3. `GitHubHttp.lastChanges`: one query for a batch, read from the fixture; no call for no requests.
4. `DiscoveryOutput`: changes recorded and read back.
5. `ChangeFetch`: batches of 50; the answers recorded; the run asks only for locations without a change.
6. `CatalogueEntries.places`: a location's last change from `changes.jsonl`; none when it is missing; README.

## Slice: image labels (planned 8 Nov; built 8 Nov)

The table's last row: the labels each registry image carries, beside the Dockerfile's own `LABEL`s, saying who set
each. Lineage's `effective.jsonl` already attributes every label (its `Fold`) to the image that set it, the
external base, or no one it can tell; the catalogue reads that rather than comparing configs again.

- Each image in `hierarchy.images` gains `labels`: per name its value (hidden as the Dockerfile's are, through dsl's
  `Secrets`, which takes facts' formatting so both write the same `(hidden sha256:...)`), its `source` and `setBy`
  (the image's config digest or the base's path):
  - `dockerfile`: set by this image, and its Dockerfile has a `LABEL` of that name;
  - `build`: set by this image, but not by its Dockerfile (added by the build: Buildah, Konflux, a `--label`);
  - `ancestor`: set by an image above it, named; `base`: by the external base, named;
  - `unknown`: lineage could not tell (Buildah's `io.buildah.version`, which every build rewrites).

Tests, in order:

1. `Secrets.shown`: a plain value as it is; a secret-named or secret-shaped value as `(hidden sha256:...)`; facts'
   `FinalConfig` using it, its tests unchanged.
2. `RegistryImages`: an image's labels with who set them, from `effective.jsonl` (the demo run's, added unedited).
3. `Hierarchies`: each source, one at a time, on the Python 3.12 image; a secret label value hidden.
4. `CatalogueRun` writing them; README.

## Slice: action fields from existing data (planned 8 Nov; built 8 Nov)

Asked 8 Nov: of the action fields, first the four the pipeline already has data for. Each entry gains `actions`:

- `releaseScope`: `released` when any location is at a released commit, `default branch only` when none is, null
  for a Dockerfile with no locations.
- `reach`: what a fix to this Dockerfile reaches below it: the estate Dockerfiles built on it, directly or not
  (through the links), their repositories and locations, and the registry images whose chain passes through an
  image built from it.
- `conversion`: the converter's text round trip for this Dockerfile (`passed`, `failed` with its first difference,
  or `not run`), from `roundtrip.jsonl` when the report is given the converter's output (`CONVERTER_DIR`, optional).
- `nearDuplicates`: the other Dockerfiles in its near-duplicate group (the report's fourth pass), the first 50 by
  blob SHA, and how many there are; candidates for one shared base or template.

`CatalogueEntry.placed` takes its growing set of estate-wide parts as one `Placement` record.

Tests, in order:

1. `Placement`: the refactor, the entry tests unchanged in what they check.
2. `ReleaseScope`: released; default branch only; no locations.
3. `Reaches`: Dockerfiles below through links, transitively, with their repositories and locations; a cycle not
   followed; registry images below through an image built from it.
4. `Conversions`: passed, failed with its difference, not run when absent or without `CONVERTER_DIR`.
5. `NearDuplicates`: the other members of its group, capped at 50 with the count; alone, none.
6. `CatalogueRun` writing `actions`; `ReportMain` reading `CONVERTER_DIR`; README; measured on an estate.

Measured 8 Nov on the combined public estate (facts rerun; converter output; no lineage): 5,250 entries, 5,157
parsed; conversion passed 5,149 and failed 8, as the round trip's committed expectation says; 46 Dockerfiles reach
others, the most sclorg's `s2i-base-container` `core/Dockerfile.rhel9` (47 Dockerfiles in 23 repositories); 86
released, 5,071 default branch only; 3,580 with near-duplicate siblings, the largest group 631.

## Slice: registry facts and the footprint list (planned 8 Nov)

Asked 8 Nov: the rest of the action fields, owner aside (`CODEOWNERS` is mostly absent, so unreliable), and the
settable footprint list.

- **Sizes and architectures**: lineage keeps, per tag, the image's compressed size (its manifest's layers summed) and,
  for an image index, the architectures it lists; each catalogue image gains `size` and `architectures` (from the
  index, else its config's).
- **Cadence**, from what lineage already holds: per image name, its pushes in the year before the newest push and the
  median days between them, and per image how many days after its parent it was built.
- **Base lag**: the final base's tag against the newest tag of that image path among Artifactory's tags, compared as
  versions only between tags of the same shape (`9.4` and `9.6`, not `9.4` and `latest`); `actions.baseLag` names
  both when the newest is later.
- **The footprint list**: `image,tag,footprint` rules, tried in order, the first match winning; a pattern without a
  `/` matches the image's name, one with a `/` its path (registry aside); an empty tag pattern matches any tag. The
  built-in rules ship as the default list and classify as today; `FOOTPRINTS` names a file whose rules come first.

Tests, in order:

1. `FootprintRules`: the default list read and each of today's cases classified the same; a name pattern, a path
   pattern, a tag pattern; a `FOOTPRINTS` rule ahead of the defaults; a bad line refused, naming the file and line.
2. Lineage: `ImageNodes.locate` finds a single manifest's size and an index's architectures; `Inventory` records them
   on the tag's row; old rows without them still read.
3. `RegistryImages` and `Hierarchies`: an image's size and architectures, from the index or its config.
4. `Cadence`: pushes in the year, the median days between them, days after the parent.
5. `BaseLag`: a newer tag of the same shape; none when the tag is the newest or of another shape.
6. `CatalogueRun` writing them; `ReportMain` reading `FOOTPRINTS`; README; measured.

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
