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
| `php-from-source` | rank 8, 54 locations: language runtime built from source | `docker-library/php` `8.4/bookworm/zts/Dockerfile` (blob `01bb301a`) | base pinned; each `set -eux; ...` script written as one command per line joined by `&&`; the first install through the builder, cleaned; the i686 compiler flags and the `/var/www/html` existence check dropped; `www-data` ownership and mode 1777 replaced by a group-0 writable `/var/www/html` and user 1001 (it ran as root); OCI revision and source labels |
| `rpm-rootfs` | rank 10, 46 locations: minimal root filesystem assembled with `rpm --root`, copied onto `scratch` | `openshift/external-secrets` `Dockerfile.ubi` (blob `00b4897b`) | the builder stage's runs joined; both copies owned by `0:0` explicitly (left unowned, the policy would give the whole root filesystem to the runtime user); `maintainer` label dropped; OCI revision and source labels; `scratch` has no digest to pin, as R1 already holds |
| `binary-on-ubi` | rank 11, 45 locations: prebuilt binary added onto a base, no build | `openshift/ci-tools` `images/private-org-peribolos-sync/Dockerfile` (blob `01799d21`) | base pinned to a 9.x tag instead of `latest`; `ADD` of a local file written as `COPY` (the builders' `add` refuses it), owned by `0:0`; user 1001 (it ran as root); `maintainer` label dropped; OCI revision and source labels |
