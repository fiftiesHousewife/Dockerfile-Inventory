package io.github.fiftieshousewife.dockerfileinventory.facts;

import org.openrewrite.docker.tree.Docker;

/** One blob's parse: the tree, or why the grammar rejected it. A failure is a finding, not an error. */
public sealed interface ParseResult {

    String blobSha();

    record Parsed(String blobSha, Docker.File file) implements ParseResult {
    }

    record Failed(String blobSha, String message) implements ParseResult {
    }
}
