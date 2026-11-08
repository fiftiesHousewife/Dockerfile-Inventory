# Estate expectations

What `./gradlew validate` expects of a real estate, outside `check`.

- `expected.json`: the validator's counts on the combined public estate under the default profile: images judged and
  not, the verdicts, and the actions per check. The test compares them exactly, so a change to a check shows its
  effect in the diff: update this file in the same commit. Its input is the facts written with the catalogue's
  fields (`VALIDATE_FACTS_DIR`, by default `out/all/facts-catalogue`) and the discovery output beside them
  (`VALIDATE_DISCOVERY_DIR`, by default `out/all/discovery`); it skips when they are missing.
