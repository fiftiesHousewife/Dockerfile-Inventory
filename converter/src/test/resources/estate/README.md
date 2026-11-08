# Estate expectations

What the round-trip tests (`./gradlew roundTrip`, `./gradlew imageRoundTrip`) expect of a real estate, outside
`check`.

- `expected.json`: how many distinct Dockerfiles of the combined public estate (`out/all/discovery`) pass the text
  round trip. A ratchet: the test fails below it; raise it in the commit that makes more pass.
- `image-round-trip.txt`: the Dockerfiles `imageRoundTrip` builds unless `IMAGE_ROUND_TRIP_DOCKERFILES` names another
  file: one per golden family (`dsl/src/test/resources/golden/README.md`), as `owner/repository path` or a blob SHA.
