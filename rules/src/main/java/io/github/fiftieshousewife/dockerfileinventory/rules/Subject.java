package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.ImageFacts;
import org.openrewrite.docker.tree.Docker;

/**
 * One distinct Dockerfile a rule looks at: its facts and parsed tree, or the parser's message when it failed.
 * Facts rules read {@code facts} (D3); text rules read {@code file}.
 */
public record Subject(String blobSha, ImageFacts facts, Docker.File file, String failure) {

    static Subject failed(String blobSha, String message) {
        return new Subject(blobSha, null, null, message);
    }

    static Subject parsed(String blobSha, ImageFacts facts, Docker.File file) {
        return new Subject(blobSha, facts, file, null);
    }

    boolean isParsed() {
        return failure == null;
    }
}
