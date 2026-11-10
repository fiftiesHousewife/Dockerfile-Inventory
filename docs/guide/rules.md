# The rules

```bash
export DISCOVERY_DIR=/data/inventory          # discovery's OUT_DIR: blobs and manifest
export LINEAGE_DIR=/data/lineage              # optional: rules about inherited users and bases
export ALLOW_LIST=/data/approved-bases.csv    # optional: imagePath,tagPrefix (rule S1)
export EOL_LIST=/data/eol.csv                 # optional: imagePath,tagPrefix,status,eolDate (rule R2)
export CATALOGUE_DIR=/data/catalogue          # optional: the catalogue stage's OUT_DIR, for R4 (over the estate)
export OUT_DIR=/data/rules
docker-inventory rules
```

Detect-only: nothing is changed. Each distinct Dockerfile is checked once; a rule whose input is missing is
skipped and listed in `rates.json`. Secret-shaped values are never written out, only a SHA-256 of the value and
its line.

| Rule | Finds | Severity |
| --- | --- | --- |
| S1 | A base, or the base at the root of an image's chain, not on the approved list | High |
| S2 | A secret-shaped `ENV` or `ARG` value (a value that is only an absolute path is not one) | Critical |
| S3 | `curl` or `wget` piped to a shell | High |
| S4 | An image that cannot run as an arbitrary non-root UID (OpenShift's default) | High |
| S5 | A `chmod` in the final image that lets everyone write (others' write bit, `o+w`, `a+w`; the sticky `1777` included) | Medium |
| S6 | An `ADD` of a URL (`http`, `https`, `git@`) without `--checksum` | Medium |
| S7 | A file named like a private key or credential (`id_rsa`, `*.key`, `*.p12`, `.netrc`, `.git-credentials`, `.pgpass`, `.pypirc`, `kubeconfig`) copied into the final image; judged by the name, nothing read from it | High |
| R1 | A base not pinned by digest (`scratch` has none) | Medium |
| R2 | An end-of-life base | Medium |
| R3 | No OCI revision or source label | Low |
| R4 | One base and tag pinned at several digests across the estate (with `CATALOGUE_DIR`): every pin but the newest is behind | Low |
| H1 | An RPM install with no clean in the same `RUN` | Low |
| H2 | A shell-form entrypoint | Low |
| H3 | Adjacent `RUN`s that could be one | Low |
| H4 | Every package upgraded in the build (`apt-get upgrade`, `dnf update` and the like, with no package named; apt's and apk's `update` only refresh the index) | Low |
| H5 | An `ADD` of local files with no tar archive to unpack, which `COPY` would do (judged by the names) | Low |
| H6 | A `chown` or `chmod` in a later layer on files the final image copied in, which stores them twice (`COPY --chown`, `--chmod` would not) | Low |
| H7 | A `chown` or `chmod` in a `RUN` inside a path a `VOLUME` declared earlier: Docker's legacy builder discards it, BuildKit keeps it | Low |
| H8 | An `rm` of what a `COPY` or `ADD` in an earlier layer wrote: the layer still holds it | Low |
| H9 | `ENV DEBIAN_FRONTEND` in the final image: a setting for the build left set in every container | Low |
| I1 | Packages installed without a version | Info |
| I2 | A Dockerfile the parser cannot read | Medium |

| File | Contents |
| --- | --- |
| `findings.jsonl` | One finding per rule per distinct Dockerfile, with its line where known; rules about inherited properties name the Dockerfile to change |
| `rates.json` | Findings, files and locations per rule; the rules skipped |
| `samples/<rule>.csv` | Up to 50 findings per rule, spread across organisations, with a `verdict` column for people to classify before any fix is enabled |

`renovate/dockerfile-estate.json` is a Renovate preset for the central instance: Dockerfile bases pinned by
digest, base updates grouped into one pull request per repository.
