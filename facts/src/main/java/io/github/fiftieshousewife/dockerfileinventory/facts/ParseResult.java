package io.github.fiftieshousewife.dockerfileinventory.facts;

import org.openrewrite.docker.tree.Docker;

/** One blob's parse: the tree, or why the grammar rejected it. A failure is a finding, not an error. */
public sealed interface ParseResult {

    String blobSha();

    /** {@code retried}: the file parsed only with the parser's known bugs worked around. */
    record Parsed(String blobSha, Docker.File file, boolean retried) implements ParseResult {

        public Parsed(String blobSha, Docker.File file) {
            this(blobSha, file, false);
        }
    }

    record Failed(String blobSha, String message) implements ParseResult {
    }
}
