# Catalogue fixtures

- `server.Dockerfile`: a two-stage Go service written for these tests in the shape of the estate's commonest Go
  archetype (a go-toolset builder, a pinned ubi-minimal final stage), with labels, an environment variable that
  holds a secret, a cleaned install, a volume, a health check, a numeric user and an exec-form start.
- `facts.jsonl`: the facts stage's row for it, written by `./gradlew :facts:run` on 8 Nov (the secret already
  hidden there), not by hand.
- `manifest.jsonl`: two locations of the same file, a released one and a fork's default branch.
- `changes.jsonl`: discovery's last change for the released location, hand-written in its shape; the fork's
  location has none.
