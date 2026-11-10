# The report and the catalogue

```bash
export FACTS_DIR=/data/facts                  # the facts stage's OUT_DIR
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export LINEAGE_DIR=/data/lineage              # optional: registry images, their chains and labels
export CONVERTER_DIR=/data/converter          # optional: the converter's OUT_DIR, for each Dockerfile's round trip
export VALIDATOR_DIR=/data/validator          # optional: the validator's OUT_DIR, for the action plan page
export STALE_AFTER_MONTHS=12                  # optional: an image unpublished this long is unmaintained
export LAYER_LIMIT=40                         # optional: an image with more layers is flagged
export FOOTPRINTS=/data/footprints.csv        # optional: footprint rules tried before the built-in ones
export OUT_DIR=/data/report
docker-inventory report
```

With the validator, run the catalogue first: `docker-inventory catalogue`
takes the same settings, without `VALIDATOR_DIR`, and writes only `catalogue/dockerfiles.jsonl`, `archetypes.jsonl`
and `passes.json`, which the validator reads; the report then writes the same files again with its pages.

Every Dockerfile location counts once. Dockerfiles are collapsed in four passes, cheapest first: identical files,
files identical once comments and whitespace are normalised, files with the same signature (base family and major
version, package managers and packages, user kind, ports, entrypoint form, stage count), and near-duplicates
(package set and base family at least 80% similar). What is left are the archetypes.

| File | Contents |
| --- | --- |
| `passes.json` | Groups and locations after each pass; k80 and k95, the fewest archetypes covering 80% and 95% of locations |
| `archetypes.jsonl` | One archetype per line, by size: its files, signature, a representative file and the repositories using it |
| `catalogue/dockerfiles.jsonl` | One line per distinct Dockerfile, described below |
| `index.html` | The summary page, described below; links to the pages below |
| `orgs/<org>.html` | The same summary for one organisation (the first part of each repository's name) |
| `hierarchy.html` | Every tree of estate Dockerfiles built on one another, from each root down, each node with its total layers, registry images, when it last changed and why it is unmaintained; how many stand alone |
| `blocked.html` | The unmaintained images in the middle of chains, the one holding back most images first, each with its reasons and the Dockerfiles whose images it holds back |
| `archetypes.html`, `archetypes-<n>.html` | Every archetype, ranked, 250 to a page, each named from what it builds and linked to the page of one of its Dockerfiles |
| `openshift.html` | With `VALIDATOR_DIR`: the action plan, how many images would break, drift or run under the validator's profile and how many fixes its actions come to, the first 250 fixes most urgent first, each naming the file to change (a Dockerfile, linked to its page, or a workload's YAML) with its copies, the Dockerfiles built on them and the locations reached; with workloads, how many would be refused admission, break, drift or run, and the first 250 worst first, each with its YAML, its containers (image, the Dockerfiles it runs or why it is unlinked, whether its tag floats) and its findings; and the actions per repository; the summary then leads with the same sentences |
| `dockerfiles/<blobSha>.html` | One Dockerfile's catalogue entry made readable: its locations; the final image; its stages, copies and ownership changes; packages and runtimes; the Dockerfiles above and below it; its registry images with their chains; its actions (release scope, reach, conversion, base lag, near-duplicates); or why it did not parse; with `VALIDATOR_DIR` and workloads, the workloads whose containers run it ("Runs in": verdict, workload, file, containers) |

Every page is drawn from `catalogue/dockerfiles.jsonl` (the archetype ranking from the same run), so the pages and
the catalogue cannot disagree. Each page stands alone (styles inside it, no scripts, nothing fetched), linked to the
others by relative links, so the folder can be copied or served as it is.

The summary counts the Dockerfiles, registry images, locations and organisations, and how many did not parse; how
many estate Dockerfiles each stands on and how many layers each carries, with those over `LAYER_LIMIT`; the
unmaintained images by reason and how many images stand on one; the final images running as root, exposing a port
below 1024 or shipping a toolchain (a compiler, build tool or `-devel` package in a final stage); the footprints; the
language runtimes, where each sits; and the unusual steps by kind. It links to the other pages and the data files.

The archetype pages leave templates, bundles, unreadable files and bases set by a variable out of the ranking, so
their count of archetypes covering 80% of locations differs from k80 in `passes.json`, which counts every location.

## The catalogue

`catalogue/dockerfiles.jsonl` describes each distinct Dockerfile (by blob SHA), parsed or not, for reporting on
what the estate builds. Secret-shaped values, and values under a secret's name, are `(hidden sha256:...)`.

| Field | Contents |
| --- | --- |
| `blobSha`, `locations` | The Dockerfile's identity, and every repository, path, scope and commit holding it, each with when the file last changed there (`lastChanged`, `lastChangedIn`) |
| `parseError` | Why it did not parse; the fields below are then empty |
| `image` | The final image as written: user and its kind, ports, entrypoint and command with their form, working directory, volumes, health check, shell, stop signal, environment and labels |
| `build.stages` | Per stage: its base (name, tag, digest, pinned) or the stage it builds on, the stages it copies from, its instructions, `RUN`s, commands, unreadable `RUN`s and layers, every copy with its origin (context, stage, image, URL, heredoc) and `--chown`/`--chmod`, every `chown`/`chgrp`/`chmod`, and unusual steps (`ADD`, `ONBUILD`, `VOLUME`, `RUN --mount`, ...) |
| `build.packages`, `build.runtimes` | Packages installed (manager, versioned, cleaned in the same `RUN`); Java, Go, Node.js and Python, each `final`, `build` (a build stage only) or `inherited` (from an estate image above it), with its evidence |
| `links` | The estate Dockerfiles that build its base (`parents`) and build on it (`children`), each from `lineage` or `inferred` with its evidence, and how many deep its chain is |
| `footprint` | The class of the base at its chain's root: micro, minimal, full, distroless, scratch, alpine, slim or unknown, by the footprint rules |
| `hierarchy.layers` | Its own, inherited and total layers, `counted` from its newest registry image or, without one, `declared` by the Dockerfiles; `overLimit` above `LAYER_LIMIT` |
| `hierarchy.images` | With `LINEAGE_DIR`: each registry image built from it (lineage credited it to this Dockerfile: `credited` in `links.jsonl`), with its created and last-pushed times, its `chain` to the root (each image's own layers, times and reasons it is unmaintained) and `blockedBy`, the unmaintained images above it; its compressed `size` and `architectures` (an index's, else its config's); its `cadence`: pushes in the year before the registry's newest push, the median days between pushes, and how many days after its parent it was built; its `provenance` (signed, attested, with an SBOM, from cosign's tags beside it); its `usage` (downloads over its tags and the latest download); and its `labels`, each with its `source`: `dockerfile` (a `LABEL` in this Dockerfile), `build` (set when this image was built, not by its Dockerfile), `ancestor` or `base` (set above it, named in `setBy`), or `unknown` |
| `hierarchy.candidateImages` | Registry images lineage could not pin to this Dockerfile alone (several Dockerfiles, a tie, or a weak link below the threshold or within the margin) that this one may have built; they count nowhere else |
| `hierarchy.unmaintained` | `every repository holding it is archived`, when that is so |
| `actions.releaseScope` | `released` when any location is at a released commit, else `default branch only` |
| `actions.reach` | What fixing it reaches: the estate Dockerfiles built on it, directly or through others, their repositories and locations, and (with `LINEAGE_DIR`) the registry images built on its images |
| `actions.conversion` | With `CONVERTER_DIR`: the converter's text round trip, `passed` or `failed` with the first difference; else `not run` |
| `actions.baseLag` | With `LINEAGE_DIR`: the final base's tag and the newest tag of that image the registry holds, when it is later (compared as versions, between tags of the same shape: `9.4` and `9.6`, not `latest`) |
| `actions.nearDuplicates` | The other Dockerfiles in its near-duplicate group (the first 50 by blob SHA) and how many there are: candidates for one shared base or template |

The footprint rules are lines of `image,tag,footprint`, tried in order, the first match winning: a pattern without a
`/` matches the base's name, one with a `/` its path (registry aside), `*` matches any text, and an empty tag pattern
any tag. The built-in rules are in `report/src/main/resources/footprints.csv`; a `FOOTPRINTS` file's rules come first,
so an estate can name its own bases, for example `ocp/*,base-rhel*,full`.

An image is unmaintained when it was not rebuilt on its updated base, or was not pushed for `STALE_AFTER_MONTHS`
before the newest push in the registry (not before today, so a rerun on the same output gives the same answer).
