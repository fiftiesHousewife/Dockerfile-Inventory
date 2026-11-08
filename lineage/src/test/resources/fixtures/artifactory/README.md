Hand-written from JFrog's documented response shapes (REST API: Get Repositories, File Info; Docker Registry
API v2: catalogue, tags list, manifests), with names chosen for each case. JCR 7.77's real replies (in
`../artifactory-jcr/`) confirm the shapes of the repository list, catalogue, tags, OCI manifest, config and storage
replies; the cases JCR did not produce (manifest lists, schema 1, an arm-only list, errors) remain unconfirmed.
- `storage-stats.json`: the storage API's `?stats` answer for a tag's `manifest.json`, in the shape Artifactory
  documents (download count, last download in epoch milliseconds).
