# The converter and the round trips

```bash
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR
export OPT_IN=/data/opt-in.txt                # optional: owner/repo per line; only these get classes
export ALLOW_LIST=/data/approved-bases.csv    # optional: approved bases, for the TODO list
export OUT_DIR=/data/converter
export JAVA_ROUND_TRIP=true                   # default; false skips compiling a class per Dockerfile
docker-inventory converter
```

Every distinct Dockerfile is converted to the builder model faithfully, so the generated class reproduces the
image as it is, and is checked by a round trip: the rendered conversion must parse back to the same model and
have the same instructions as the original. A Dockerfile that already uses heredocs or `RUN --mount` is rendered
for a builder that has them. Policy breaches become `// TODO(<rule>)` comments, for later fixes. Then every
Dockerfile that passed has its class generated, compiled and run, and passes the Java round trip when the class
builds the same model and so the same Dockerfile.

| File | Contents |
| --- | --- |
| `generated/images/<owner>/<Name>Image.java` | One builder class per opted-in Dockerfile that passed the round trip |
| `roundtrip.jsonl` | Each Dockerfile, whether it passed, and the first difference when not; with the Java round trip, `javaPassed` and `javaDifference` |
| `coverage.json` | Typed instructions over all instructions per Dockerfile, how often each reason left an instruction untyped, and `javaRoundTrip`: classes that rebuilt their Dockerfile, of those tried |

## Round-trip tests on a real estate

Two tests check the converter against real Dockerfiles. Both read a discovery output, so they run as well on an
enterprise estate as on the public one.

They exist because the converter is what lets a team hand its Dockerfile over to the builders: the generated class
replaces the file, and every later fix is made by changing the class and regenerating. That is safe only if
regenerating changes nothing a running container can see. `roundTrip` checks the text across a whole estate: every
instruction understood, the rewritten Dockerfile the same instructions, the generated class rebuilding it.
`imageRoundTrip` checks what the text cannot show, by building the original and the regenerated Dockerfile and
comparing the images they make: the evidence a team needs before it opts in.

```bash
export ROUND_TRIP_DISCOVERY_DIR=out/all/discovery     # any discovery output (this is the default)
./gradlew roundTrip                                   # every Dockerfile in it converted; no Docker needed

export IMAGE_ROUND_TRIP_DOCKERFILES=my-dockerfiles.txt   # optional: which Dockerfiles to build (default below)
export GHE_API_URL=... GHE_TOKEN_FILE=...                # optional: where to clone them from, as for discovery
export REGISTRY_AUTH_FILE=~/auth.json                    # optional: a login for bases behind one
export REGISTRIES_CONF=~/registries.conf                 # optional: public names pulled through a mirror
export BUILD_TIMEOUT=10                                  # optional: minutes one build may take (default 10)
export IMAGE_ROUND_TRIP_PARALLEL=4                       # optional: Dockerfiles built at once (default 4)
./gradlew imageRoundTrip                              # where Docker runs: built twice and compared
```

`roundTrip` converts every distinct Dockerfile and fails unless every instruction is typed, at least the committed
number of Dockerfiles pass the text round trip, and every passing Dockerfile's generated class rebuilds it.

`imageRoundTrip` takes the Dockerfiles listed in `IMAGE_ROUND_TRIP_DOCKERFILES`, one per line as `owner/repository
path/to/Dockerfile` or a blob SHA (`#` starts a comment); by default the ten in
`converter/src/test/resources/estate/image-round-trip.txt`, one per family of image. For each, the Dockerfile and
the converter's rendering of it are built with Buildah from the same context at the commit discovery scanned, and
the two images compared. The context is the Dockerfile's directory, else the repository root, else its directory
with the sources CI makes before the build stubbed as empty files in both builds (the next context is tried only
when a build failed finding a `COPY` or `ADD` source; any other failure, or a build over `BUILD_TIMEOUT`, leaves
the pair not built, saying why, and each pair's images are removed once it is done); each secret a `RUN` mounts is
given an empty one. The same image means the same config (labels the builder writes at build time aside) and the
same files: path, type, mode, owner, link target and content. Timestamps are never compared, nor the content of
files that record when they were built (package databases, logs, caches, Python bytecode, `/tmp`,
`/etc/machine-id`), nor the build container's hostname; those files are still checked to exist with the same mode
and owner. The original is also built a second time, as a control: a difference the two builds of the original
show too (a certificate generated at build time, a binary that embeds its build date) is set aside as noise and
listed, and only what remains counts. The test fails on any pair that differs and on any Dockerfile that did not
build, saying why, so every sample on the list must build; a build whose download failed on the network or a
mirror (a TLS or connection error, a hash-sum mismatch) is tried once more, its first log kept as `*.first.log`. `IMAGE_ROUND_TRIP_PARALLEL` Dockerfiles are built at once
(those sharing a checkout one after another), and each Dockerfile's regenerated and control builds run together;
each result gives its seconds, and the run its total. Images are pulled and built only, never pushed.

| File | Contents |
| --- | --- |
| `converter/build/image-round-trip/comparison.jsonl` | Per Dockerfile: built or why not, the same or not, each difference, the stubs built on |
| `converter/build/image-round-trip/work/<sha>/` | The regenerated Dockerfile and each build's log |
