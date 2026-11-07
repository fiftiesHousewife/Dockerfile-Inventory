# About 15,000 Dockerfiles: plan

Status: planned 7 Nov; question answered (locations); discovery runs are the owner's.

The owner wants the public test estate grown to about 15,000 Dockerfiles, nearer the real estate's 20,000, to see
how the pipeline and the report behave at that size before the enterprise run.

## Where we are

Six organisations, combined (`combined-report.md`): 8,199 locations, 1,453 repositories, 5,250 distinct files. About
6,800 more locations are needed (the target counts locations: question 1).

## Candidates (public repositories, 7 Nov)

Chosen for looking like the enterprise: RHEL and UBI bases, Konflux and OpenShift builds, operators, multi-stage Go.
Expected locations use our estates' rates (2-8 per repository; konflux-ci's 20 came from copied bundles).

| Tier | Organisation | Repositories | Expected locations | Why |
| --- | --- | --- | --- | --- |
| 1 | RedHatInsights | 682 | 1,400-2,000 | Red Hat's own SaaS services: UBI bases, Konflux builds, many small services, the closest to an enterprise estate |
| 1 | stolostron | 375 | 1,000-1,500 | Red Hat Advanced Cluster Management: `Dockerfile` beside `Dockerfile.rhtap`, operators |
| 1 | openshift-pipelines | 98 | 400-600 | Tekton on OpenShift: many images per repository |
| 1 | redhat-cop | 214 | 300-500 | Community of practice: helper images, varied quality, the closest to hand-written enterprise Dockerfiles |
| 1 | kubevirt | 112 | 300-500 | Virtualisation: large multi-stage builds, Bazel-generated images |
| 1 | operator-framework | 62 | 200-300 | Operator SDK and OLM |
| 1 | containers | 104 | 200-400 | Podman, Buildah, Skopeo: Fedora and UBI bases |
| 1 | quay | 67 | 150-250 | Quay registry |
| 1 | ansible | 308 | 300-500 | Execution environments |
| 2 | kubernetes | 81 | 600-900 | test-infra and perf-tests images: Debian and distroless, not RHEL |
| 2 | kubernetes-sigs | 210 | 500-800 | Controllers and tools |

Tier 1 alone should give about 4,500-6,500; tier 2 is run only if tier 1 leaves us short, as it moves the estate
away from RHEL. Larger organisations (apache 3,176 repositories, jenkinsci 2,805, elastic 972) would overshoot and
look least like the enterprise.

## Steps

1. Discovery, one organisation at a time, by the owner (`scripts/discover.sh https://github.com/<org>
   out/<org>/discovery`), largest first. On github.com the token allows 5,000 REST calls an hour; one organisation of
   700 repositories needs about 700 tree calls and a few dozen GraphQL batches, so each run fits in the hour. The
   corporate-readiness work (retries, pacing, token files) makes a long sequence safer but is not needed first.
2. After each organisation: facts, rules, converter on it alone; its numbers beside the others in `more-estates.md`;
   a fix round planned the usual way if it shows something new (as bitnami did).
3. `scripts/combine.sh` over every source, stopping when the combined count passes the target.
4. At that size, measured: each stage's run time and peak memory (facts, rules, converter, report), the report's
   size and load time (with the usability changes, `report-usability.md`), and the near-duplicate grouping's time
   (MinHash buckets: should stay near linear).
5. The combined dashboard published and the README's example link updated.

## Question

1. Count: about 15,000 Dockerfile locations (each path in each repository, the report's headline number; we have
   8,199), or about 15,000 distinct files (we have 5,250; nearly three times as many organisations)?

Answer (7 Nov): locations.

## Risks

- Copies: an organisation of generated bundles (as konflux-ci's operator bundles) adds locations but few distinct
  files; the per-organisation table shows both, and the target is checked against the right one.
- Rate limits: the token is shared by the owner's other GitHub use; discovery resumes after a stop.
- Report size: 15,000 locations and 4,000 or more archetypes; the usability work's click-through pages keep the
  summary small.
