package io.github.fiftieshousewife.dockerfileinventory.discovery;

/**
 * A repository as enumeration found it. Archived and fork flags are kept as facts rather than filtered out,
 * so reports can decide what to exclude. {@code headCommit} is null for an empty repository.
 */
public record Repository(String nameWithOwner, String defaultBranch, String headCommit,
                         boolean archived, boolean fork, String pushedAt) {
}
