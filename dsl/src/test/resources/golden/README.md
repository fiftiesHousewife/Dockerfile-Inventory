# Golden Dockerfiles

One per family of image on the combined public estate, the largest archetype of each family standing for it
(`docs/plans/dsl-completion.md`, S2). Each is the archetype's representative rewritten to the policy with
`ImageBuilder` (`Goldens`), rendered here; `GoldenTest` keeps the file and the builder equal, and `rules`'
`GoldenAcceptanceTest` finds nothing in any of them. `allow.csv` is the golden policy's approved bases, each
family's own; digests are placeholders.

| Golden | Archetype | Representative | What the rewrite changed |
| --- | --- | --- | --- |
| `bitnami-app` | rank 2, 129 locations: bitnami application image | `bitnami/containers` `bitnami/notation/1/debian-12/Dockerfile` (blob `04f0a7c5`) | base pinned; `install_packages` replaced by an `apt-get` install cleaned in the same `RUN`; the downloads secret mount dropped (the default target has no `RUN --mount`; the build argument carries the URL); the download without `cd`; `apt-get upgrade` dropped; `chmod g+rwX` replaced by group-0 writable paths; OCI revision and source labels |
| `olm-catalog` | rank 3, 97 locations: OLM file-based catalog | `openshift/cert-manager-operator-release` `catalogs/v4.22/Containerfile` (blob `17249dbc`) | base pinned; the catalog cache made group-0 writable and the image run as 1001 (it ran as the base's root); OCI revision and source labels |
| `go-operator` | rank 4, 84 locations: Go operator built in a builder stage | `openshift/ptp-operator` `Dockerfile.ocp` (blob `76dc5dae`) | both bases pinned; copies owned by 1001:0 and the image run as 1001 (it ran as the base's root); `com.redhat.delivery.appregistry` and `maintainer` labels dropped; OCI revision and source labels |
