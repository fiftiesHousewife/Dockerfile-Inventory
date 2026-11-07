package io.github.fiftieshousewife.dockerfileinventory.discovery;

record Repository(String nameWithOwner, String defaultBranch, String headCommit,
                  boolean archived, boolean fork, String pushedAt) {
}
