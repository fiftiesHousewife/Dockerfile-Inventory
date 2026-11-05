package io.github.fiftieshousewife.dockerfileinventory.discovery;

/**
 * A fetched blob. GitHub returns no text for binary blobs and cuts very large ones; both are findings
 * rather than Dockerfiles to parse.
 */
public record BlobText(String sha, String text, boolean truncated, boolean binary) {
}
