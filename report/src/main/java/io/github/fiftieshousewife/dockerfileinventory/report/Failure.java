package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

record Failure(String blobSha, String message, List<Location> locations) {

    Failure {
        locations = List.copyOf(locations);
    }
}
