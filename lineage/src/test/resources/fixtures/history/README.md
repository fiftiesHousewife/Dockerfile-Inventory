History from the image configs of public Red Hat images (linux/amd64 `latest`, fetched 6 Nov 2026 from
registry.access.redhat.com): `ubi9/ubi-minimal`, `ubi9/ubi`, `ubi9/python-311`, `ubi9/nodejs-20`. They are built by
Konflux (Tekton and Buildah). Only `rootfs` and `history` are kept. These stand in for the estate's own image
history until Artifactory access exists (Phase 1b, Q4 and Q13).

`okd-cluster-storage-operator.json`: OKD's `quay.io/okd/scos-content:5.0.0-okd-scos.5-cluster-storage-operator`
(linux/amd64, fetched 7 Nov 2026), built by Red Hat's release tooling (ART's doozer, on Konflux) from
`openshift/cluster-storage-operator`'s `Dockerfile.rhel7`. Squashed (most steps `empty_layer`), and the Dockerfile's
steps are wrapped by the tooling's own: `ENV ART_BUILD_*`, `/tmp/art` and its yum repositories, `ENV __doozer=`,
then after them `USER 0`, the repository swap back and `rm -rf /tmp/art`. Only `rootfs` and `history` are kept.
