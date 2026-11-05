package io.github.fiftieshousewife.dockerfileinventory.facts;

/** Who the final stage runs as, as far as the Dockerfile alone says. OpenShift needs a numeric non-root user. */
public enum UserKind {
    /** No {@code USER}, or {@code root}, or uid 0. */
    ROOT,
    NUMERIC,
    NAMED,
    /** Built from a variable the Dockerfile does not define. */
    UNRESOLVED
}
