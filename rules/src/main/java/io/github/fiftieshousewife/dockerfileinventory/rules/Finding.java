package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

/**
 * One rule's finding on one distinct Dockerfile (D4). {@code line} is 1-based where known. {@code valueHash} is the
 * SHA-256 of a secret-shaped value (S2), never the value. {@code contributing} names the Dockerfile group an
 * effective finding should be fixed in (D5).
 */
public record Finding(String rule, Severity severity, String blobSha, Integer line, String message, String valueHash,
                      List<String> contributing) {

    public Finding {
        contributing = List.copyOf(contributing);
    }
}
