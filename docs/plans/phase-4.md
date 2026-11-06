# Phase 4 — DSL, builders, renderer: plan

Status: built 6 Nov; Phase 4b (central generator repository, PR job) waits on a GHE write token. Slices are built test-first, as in the earlier phases.

Phase 4 builds an immutable instruction model, typed builders that enforce policy, and one renderer that both the
builders and Phase 3's fixes call. Module: `dsl`, with no dependency on OpenRewrite. Acceptance: the rendered
Dockerfile of every golden image passes the Phase 3 rules with zero findings, and the builders refuse each
invariant breach in a test.

## Answers (6 Nov)

| # | Question | Answer | What it changes |
| --- | --- | --- | --- |
| Q1 | Where generator classes live | One central repository | Teams need no Java toolchain; a job regenerates Dockerfiles and raises PRs into the owning repositories. That repository and job are Phase 4b (needs a GHE token with write access); this plan builds the library they use |
| Q2 | API | Java builders only | No YAML; Phase 5's converter emits Java builders, so there is one surface |
| Q3 | First goldens | Hand-picked now, real archetypes later | Four representative images now; the top real archetypes replace them after the first Phase 2 run |
| Q4 | Group-0 permissions (S4) | Declared paths; the builder adds the step | `writablePaths(...)` renders `chgrp -R 0 <paths> && chmod -R g=u <paths>` in one `RUN` before `USER` |

## Decisions taken without asking (say if any is wrong)

| # | Decision | Why |
| --- | --- | --- |
| D1 | The approved bases reach a builder as a `Policy` (the same `imagePath,tagPrefix` list Phase 3's S1 reads), passed to `build(policy)` | One list for detection and construction |
| D2 | The default render target has no heredocs and no `RUN --mount`; a target can allow either | The Buildah version is unknown (delivery plan); Phase 1's counts can widen it |
| D3 | `Raw` is allowed but every use is listed in the build result, for review | Delivery plan: "Raw allowed but reported" |
| D4 | The Phase 3 acceptance test lives in `rules`'s tests (rules may depend on `dsl`), so `dsl` stays free of OpenRewrite | The delivery plan's layering |
| D5 | Package managers render as: microdnf `microdnf install -y --nodocs --setopt=install_weak_deps=0 ... && microdnf clean all`; dnf and yum the same with their names; apk `apk add --no-cache ...`; apt `apt-get update && apt-get install -y --no-install-recommends ... && rm -rf /var/lib/apt/lists/*` | Each cleans in the same `RUN` (H1) and keeps the layer small |

## Slices

1. **Model.** Sealed `Instruction` records: `From`, `Arg`, `Env`, `Run`, `Copy`, `User`, `Workdir`, `Expose`,
   `Entrypoint`, `Cmd`, `Label`, `Raw`; `RunBody` is `Install` (a `PackageInstall`) or `Shell`; `BaseImage`;
   `Stage`; `Dockerfile`.
   1. A digest must be `sha256:` and 64 hex characters; a port 1 to 65535; a user and a working directory non-blank.
   2. Lists given to a record are copied, so the model cannot change after it is built.
2. **Package installs.** `PackageInstallRenderer` per manager, and `forBase(BaseImage)`.
   3. Each manager renders its install and clean as in D5, packages in the order given.
   4. `forBase` picks microdnf for `ubi-minimal`, dnf for `ubi`, apk for `alpine`, apt for `debian` and
      `ubuntu`; any other base needs the manager named, or it is an error.
3. **Renderer.** `DockerfileRenderer` with a `Target`.
   5. Each instruction renders as Docker reads it; exec forms as JSON arrays, quoted and escaped.
   6. A long `RUN` breaks after each `&&` with a backslash continuation, so diffs stay readable.
   7. Stages render with their names; a blank line separates stages.
   8. A heredoc or `--mount` is refused unless the target allows it.
   9. Rendering is stable: the same model always renders the same text.
4. **Builders.** `ImageBuilder` (fluent), `build(policy)` returning the `Dockerfile` and the `Raw` uses, or
   throwing `PolicyViolation` listing every breach, each with its Phase 3 rule ID.
   10. S4: the final user must be numeric and non-root.
   11. S4: declared writable paths render the group-0 step before `USER`.
   12. S1 and R1: the base must be on the policy's list and pinned by digest.
   13. H2: the entrypoint is exec form; a shell-form one is only possible through `Raw`.
   14. H1: installs go through `PackageInstall`, so they always clean; a `Raw` or shell `RUN` that installs without
       cleaning is a breach.
   15. Several breaches are reported together.
   16. `Raw` uses are listed in the result (D3).
5. **Goldens.** Four hand-picked images (Q3): a Java service on `ubi-minimal`, a Node service on `ubi-minimal`, a
   Python service on `ubi`, and a multi-stage Go build. Their rendered Dockerfiles are committed under
   `src/test/resources/golden/`.
   17. Each golden builder renders exactly its committed file.
   18. In `rules`: every golden passes the per-file rules with zero findings (D4).

   As built: the builder gained `arg()`, so the OCI labels R3 requires can take their values at build time. The
   acceptance test leaves out I1, since package pinning is not an invariant. Breaking a golden on purpose first
   did not fail it: Gradle did not know `rules`' tests read `../dsl` files and treated them as up to date. The
   goldens, and `testdata/` for `facts` and `lineage`, are now declared test inputs, and the broken golden fails.

6. **Merge adjacent `RUN`s (H3), 6 Nov.** `build()` joins `RUN`s that follow each other in one stage into one
   `RUN` with `&&`, so H3 holds by construction. `model()` keeps them as written, for Phase 5. A `RUN` whose
   commands change shell state for what follows (`cd`, `export`, `source`, `.`, `set`, `umask`) is not joined to
   the next, since joining would carry that state into it. Tests, in order:
   19. Adjacent `RUN`s in a stage, including an install and the group-0 step, render as one `RUN`.
   20. `RUN`s separated by another instruction, or in different stages, stay apart.
   21. A `RUN` that changes shell state is not joined to the `RUN` after it.
   22. `model()` keeps adjacent `RUN`s as written.
