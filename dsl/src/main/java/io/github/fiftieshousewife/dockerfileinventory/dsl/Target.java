package io.github.fiftieshousewife.dockerfileinventory.dsl;

/** What the builder that will read the Dockerfile supports (Phase 4, D2). */
public record Target(boolean heredocs, boolean runMounts) {

    /** No heredocs and no {@code RUN --mount}: the Buildah version is unknown. */
    public static final Target DEFAULT = new Target(false, false);
}
