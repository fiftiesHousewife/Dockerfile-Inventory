package io.github.fiftieshousewife.dockerfileinventory.discovery;

/** Where a repository's scanned commit came from. */
public enum Scope {
    /** The commit the release database records as last released. */
    RELEASED,
    /** No release entry: the default branch head, flagged so reports can show how much is not release-scoped. */
    DEFAULT_BRANCH
}
