package io.github.fiftieshousewife.dockerfileinventory.rules;

/** One distinct Dockerfile a rule looks at. */
public record Subject(String blobSha, String failure) {

    static Subject failed(String blobSha, String message) {
        return new Subject(blobSha, message);
    }
}
