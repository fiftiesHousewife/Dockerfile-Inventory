# Image comparison fixtures

Written by hand in the layout `scripts/compare-images.sh` writes: one directory per blob SHA, holding `original/`
and `regenerated/`, each with the image's config (`config.json`, `buildah inspect`'s Docker config), its files
(`files.txt`, `find -printf '%y\t%m\t%U\t%G\t%p\t%l\n'`) and their sums (`sums.txt`, `sha256sum`), or a
`failure.txt` when a build failed.

- `aaa`: the same image; only a build-time label and a log's content differ, both ignored.
- `bbb`: the regenerated image runs as root and its binary lost its execute bits.
- `ccc`: the original did not build.
