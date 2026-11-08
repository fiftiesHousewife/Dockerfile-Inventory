# Estate expectations

What the tagged tests (`./gradlew estateTest`, `./gradlew imageTest`) expect of a real estate, outside `check`.

- `expected.json`: how many distinct Dockerfiles of the combined public estate (`out/all/discovery`) pass the text
  round trip. A ratchet: the test fails below it; raise it in the commit that makes more pass.
- `compare-images.txt`: the Dockerfiles the image comparison builds, one blob SHA per line: the representatives of
  the ten golden families (`dsl/src/test/resources/golden/README.md`).
