# Dockerfile Inventory: the problem and the approach

## The problem

A large organisation builds its container images from Dockerfiles scattered across thousands of repositories:
about 20,000 of them in the estate this pipeline was written for, in GitHub Enterprise, built by Tekton into images
held in Artifactory and run on OpenShift. Nobody wrote them to a plan. Each team copied a Dockerfile it found,
changed what it needed, and moved on. The result is familiar to anyone who has looked: the same handful of patterns
repeated with small differences, bases that were current when copied and are end of life now, images that run as
root because the base did, packages installed and never pinned, caches never cleaned, and every so often a password
in an `ENV`.

The questions a platform team needs answered are simple to ask and hard to answer at that scale:

- What is the estate actually built from? Which bases, which package managers, which users?
- How many genuinely different kinds of Dockerfile are there, once copies and near-copies are folded together? If
  twenty archetypes cover most of the estate, standardising twenty is a project; standardising 20,000 is not.
- Which images run what they shouldn't, and which Dockerfile in a chain of `FROM`s should change to fix it?
- Can the fixes be made safely, at scale, without hand-editing thousands of files?

Three things make this harder than it sounds. The Dockerfile on a repository's default branch is often not what was
built and released, so reading `main` gives the wrong answer. A Dockerfile alone does not say what an image ends up
as: its user, environment and entrypoint are inherited through a chain of bases, some built in-house and some not.
And Dockerfiles are not one language but two: Docker's own instructions, and the shell inside every `RUN`, which is
where packages are installed and most of the interesting decisions live.

## The approach

The pipeline is a set of stages that hand files to each other. Each can be rerun alone, and its output read by a
person, which matters when the input is an estate nobody fully understands yet.

**Discovery** finds every Dockerfile at the commit that was last released, taken from the release database's export,
and falls back to the default branch only where there is no release, flagging it. It reads repository trees over
GitHub's REST API and fetches file contents in GraphQL batches, so no repository is cloned. Every distinct file is
stored once by its content hash: the estate's 20,000 locations are far fewer distinct files, and everything after
discovery works per file and remembers every location.

**Facts** parses each file with a real Dockerfile parser (OpenRewrite's `rewrite-docker`), and reads each `RUN` body
with a shell reader of our own that understands enough of POSIX shell and bash to find package installs inside
`if`, `case`, loops, functions, arrays and command substitutions, while counting what it cannot read rather than
guessing. Each Dockerfile becomes a small set of facts: its final base, its package calls, its user, its ports, its
entrypoint form, the BuildKit features it needs, and the steps of its final image in a normal form.

**Lineage** reads the images in Artifactory and rebuilds the family tree from their layers: an image's parent is the
image whose layers are a prefix of its own. It then links each image back to the Dockerfile that built it, by
comparing the image's own build history with each Dockerfile's steps, or, when an image is labelled with its source
repository, by looking only in that repository. With the tree and the links it folds each image's effective
properties down the chain, and says for each which image set it. It also lists the images it could not link, and
the images built on a base that has since been replaced.

**Report** groups the Dockerfiles in passes, from exact copies to near-duplicates (MinHash over their normal forms),
into archetypes, and shows how few archetypes cover most of the estate, alongside bases, users and package managers,
as a dashboard and as tables for the organisation's own reporting database.

**Rules** checks the estate against house rules, detect-only for now: secrets, unpinned bases, uncleaned installs,
shell-form entrypoints, root users, images not ready for OpenShift's arbitrary user IDs. A rule about what an image
inherits runs on the effective image from lineage and names the Dockerfile in the chain that should change. Each
rule's findings are sampled for people to classify before any fix is turned on.

**The DSL and the converter** are where fixes come from. A small Java model of a Dockerfile, typed builders that
enforce the house policy, and one renderer that writes the text. The converter reads an existing Dockerfile into
that model and writes it back; when the round trip gives the same Dockerfile, the file can be maintained as code and
regenerated, and every fix goes through the same renderer, so migrated and new Dockerfiles converge on the same lines.

## Decisions that shaped it

- **Released commits, not default branches.** The point is what runs, not what someone is working on.
- **Files, not objects, between stages.** Slower than one process; far easier to rerun, inspect and trust.
- **The content hash is the identity.** Parse once, count every location.
- **Count what cannot be read.** An unreadable `RUN` or an unparseable file is a number on the dashboard, not a
  silent gap; every fix round on the public estates started from those numbers.
- **Real Dockerfiles as fixtures.** The parser and the shell reader are tested on trimmed lines from real files, and
  the contracts between facts and lineage on real images built from real Dockerfiles.
- **The parser is ours to fix.** Fifteen bugs found in `rewrite-docker` on public estates were reduced to minimal
  reproductions; twelve are worked around by rewriting text before a second parse. OpenRewrite's last open release is
  8.90.4 (Apache 2.0) and its newer modules are source-available, so the parser is being forked here and the bugs
  fixed in it (`plans/parser-fork.md`).
- **Corporate networks are the normal case.** Proxies, a corporate CA, rotated tokens, rate limits and refused
  repositories are handled and configured, not assumed away (`plans/corporate-readiness.md`).

## Where it has been tried

Before the enterprise run, the pipeline has been run on public GitHub organisations chosen to look like it: sclorg,
openshift, konflux-ci, redhat-developer, docker-library and bitnami, 8,199 Dockerfile locations combined, with
lineage tried on images built locally with Buildah and on 30 of OKD's published images. Each estate found problems
the last did not, and each was fixed with a test from its real lines; `plans/more-estates.md` keeps the numbers. The
next step is about 15,000 locations (`plans/fifteen-thousand.md`), then the enterprise pilot.
