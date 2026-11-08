package io.github.fiftieshousewife.dockerfile.model;

/** What the builder that will read the Dockerfile supports, so the renderer refuses what it cannot read. */
public record Target(boolean heredocs, boolean runMounts) {

    /** No heredocs and no {@code RUN --mount}: the Buildah version is unknown. */
    public static final Target DEFAULT = new Target(false, false);
}
