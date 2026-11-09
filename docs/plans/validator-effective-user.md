# The validator reads the effective user from lineage: plan

Status: planned 9 Nov; the question answered 9 Nov; built 9 Nov.

## Why

P1 judges the user an image runs as. A Dockerfile that sets no `USER` runs as its base's, which the Dockerfile
cannot say, so today P1 marks it inferred (2,982 P1 actions on the combined estate, most of them inferred). Lineage
knows the user each registry image runs as and which image in its chain set it (`effective.jsonl`), and since
[link-credit-in-lineage](link-credit-in-lineage.md) which Dockerfile each image was built from (`credited`).

## Measured before (9 Nov)

- OKD: 18 Dockerfiles credited to an image. 17 set no user, and their images run as `0`, set by nothing lineage
  could place (OKD's build records no `FROM`); 1 sets `nobody`, as its image shows.
- The sclorg demo: 3 credited. s2i-core and s2i-base set no user and their images set none (root); python 3.12 sets
  1001 itself.
- So on the stored estates lineage confirms 19 inferred root findings and clears none. It would clear one where a
  base held in the registry sets a numeric non-root user; the combined estate has no lineage, so it is unchanged.

## Design

- `ValidatorMain` reads an optional `LINEAGE_DIR`. Without it nothing changes.
- `EffectiveUsers` (validator) reads `links.jsonl` and `effective.jsonl`: for each Dockerfile, the effective users of
  the images credited to it, with who set each (an image in the chain, an external base, or unknown).
- P1 judges the effective user when the Dockerfile has one: its certainty becomes read, and its message says the
  user comes from the image (and names the setter when lineage knows it). A Dockerfile without credited images is
  judged as today.
- When the effective user is numeric and not root, P1 finds nothing (asked 9 Nov: clear it, since the image does
  run as OpenShift needs). When a Dockerfile's images disagree, the worst counts: root, then a named user, then a
  numeric one.

## Tests, in order

1. `EffectiveUsers.read`: a credited link's image's user, with its setter; an uncredited link gives nothing.
2. `Subject` carries the effective user; `FinalUser` on it: root read, a named user, a numeric non-root user.
3. `ValidatorRun` with a lineage directory; `ValidatorMain` reads `LINEAGE_DIR`.
4. Measured: OKD and the demo with and without lineage; the combined estate's ratchet unchanged.

## Questions answered

1. The Dockerfile sets no user but its images run as a numeric non-root user set by their base: P1 is cleared
   (9 Nov).

## Measured (9 Nov)

The validator run with and without `LINEAGE_DIR` (lineage rerun offline as in
[link-credit-in-lineage](link-credit-in-lineage.md)):

- OKD (2,440 judged): verdicts the same; 17 P1 findings inferred become read ("its registry images run as root"),
  and `nobody`'s now names the image that set it. No action added or removed.
- The sclorg demo (568 judged; facts regenerated, as `out/demo5/facts` predates the catalogue's fields): verdicts the
  same; s2i-core's and s2i-base's P1 become read.
- The combined estate, without lineage: `./gradlew validate` unchanged.

## Process

`ValidatorMain` reading `LINEAGE_DIR` was written with `ValidatorRun`'s change, before its own test; the test was
added after and seen to fail with the setting ignored. The worse-user test first passed by the order of its rows;
the rows were swapped so it fails without the merge.
