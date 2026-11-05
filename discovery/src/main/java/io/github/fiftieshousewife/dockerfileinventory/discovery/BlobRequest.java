package io.github.fiftieshousewife.dockerfileinventory.discovery;

/** A blob to fetch, with one repository known to hold it. */
public record BlobRequest(String repository, String sha) {
}
