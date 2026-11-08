# Linking images to Dockerfiles from data alone: plan

Status: planned 8 Nov; questions answered 8 Nov; built 8 Nov.

## Why

Asked 8 Nov, after OKD's 30 images all came out WEAK and so stopped counting in the catalogue: the owner wants a
deterministic link based on data, not on labels. Today a source label (`io.openshift.build.source-location`,
`org.opencontainers.image.source`) narrows an image's candidates to one repository, so a WEAK link means different
things depending on whether the image named its repository; and the catalogue credits only exact matches, so a
release pipeline that rewrites a step (OKD's injects labels, build arguments and environment) leaves the right
Dockerfile uncredited.

## Design

- **No label narrowing** (answered 8 Nov: drop it). An image's candidates are the Dockerfiles built on its base
  path, read from the image's own history; labels decide nothing. (A Dockerfile's `LABEL` keys still count as one
  step in the alignment: that compares the image's content with the Dockerfile's, not a claim.)
- **Scores recorded**. A link that is not an exact match carries its best alignment score and the runner-up's
  (the next best Dockerfile with different steps), so anyone reading `links.jsonl` sees how close the call was.
- **Credit by score and margin**. The catalogue credits an image to a Dockerfile when its steps match exactly (one
  Dockerfile, or identical ones) or when the best alignment reaches a threshold and beats the runner-up by a margin;
  otherwise the Dockerfiles stay candidates. The same inputs always give the same links.
- **The numbers from data**. The threshold and margin are chosen by rerunning the analysis offline on the stored
  OKD and demo outputs and reading the score distributions of the pairs known to be right, and are recorded here
  with the measurement; both are settings, so an estate can tighten them.

## Tests, in order

1. `Alignment`: the best score and the runner-up's on a link; a tie has no margin; a single candidate has no
   runner-up.
2. `Links`: a source label no longer narrows the candidates; an image with no base path in its history has none.
3. `links.jsonl` carries the scores; the catalogue reads them.
4. The catalogue's crediting rule: exact; aligned above the threshold with the margin; below it, or within the
   margin, a candidate. `ReportMain` reads `LINK_THRESHOLD` and `LINK_MARGIN`.
5. Measured: the offline analysis on OKD and the demo, the distributions, the chosen numbers; README.

## Found on the way

Without labels, the OKD images found no candidates at all: OKD's build records no `FROM` in an image's history, so
there was no base path to search by (asked 8 Nov, and agreed: find candidates by the steps themselves). A
`StepIndex` maps each step (labels aside) to the Dockerfiles holding it; an image's candidates are those on its base
and those sharing one of its rare steps, held by `StepIndex.RARE` (20) Dockerfiles or fewer, so the search stays cheap
on a large estate.

## Measured (8 Nov)

The offline analysis rerun on stored outputs, against the label-based links written before:

- Demo (sclorg, five images): every link the same.
- OKD (34 images): 30 the same; 3 weak links became none, their Dockerfiles holding only `COPY` steps into
  `/usr/bin/` or `/`, which 144 to 307 OKD Dockerfiles share (an image's history records a copy as a content hash and
  a destination, so the destination is all there is to compare: on content alone those images could be any of them);
  1 gained a second candidate, two Dockerfiles in different repositories with identical steps.
- The fixture bank's 34 real images all link as before; the contracts and every other test unchanged.
- OKD's 23 weak links: correct pairs score 0.8 to 1.0; the closest beats its runner-up by 0.06, the rest by 0.17 or
  more or have none. So `LINK_THRESHOLD` 0.8 (the alignment's minimum) and `LINK_MARGIN` 0.05. With them, the OKD
  catalogue credits 18 images to their Dockerfiles (none before), 10 Dockerfiles keeping candidates.
