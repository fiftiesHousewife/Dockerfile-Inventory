package io.github.fiftieshousewife.dockerfileinventory.report;

/** Where a Dockerfile sits: one unit of the estate (Q3). */
public record Location(String repository, String path, String scope) {
}
