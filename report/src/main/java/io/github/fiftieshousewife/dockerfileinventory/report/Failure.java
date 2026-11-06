package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

/** A Dockerfile the parser rejected; it still counts in the estate (D5). */
public record Failure(String blobSha, String message, List<Location> locations) {

    public Failure {
        locations = List.copyOf(locations);
    }
}
