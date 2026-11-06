package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

/** One archetype after all four passes, by rank. */
public record Archetype(int rank, String key, int locations, List<String> blobShas, String signature,
                        String representative, List<String> repositories) {

    public Archetype {
        blobShas = List.copyOf(blobShas);
        repositories = List.copyOf(repositories);
    }
}
