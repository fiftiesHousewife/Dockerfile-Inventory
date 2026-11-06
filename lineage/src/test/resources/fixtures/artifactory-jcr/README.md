JFrog Container Registry 7.77.17's real replies to every request lineage's `ArtifactoryHttp` makes, recorded by
`scripts/record-jcr-fixtures.sh` from the local run of `scripts/lineage-demo.sh` (6 Nov): CentOS Stream 9 mirrored,
and four sclorg images built with Buildah 1.37 from discovered Dockerfiles. Bodies are byte for byte (a config's
name is the digest of its bytes); `index.tsv` gives, per request path, the file, status, Content-Type and
Docker-Content-Digest, for `RecordedJcrRunTest`'s fake server. No token is recorded. The configs are also the image
side of the contracts in `testdata/contracts/sclorg-c9s-*`.
