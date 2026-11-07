package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

record Archetype(int rank, String key, int locations, List<String> blobShas, String signature,
                        String representative, List<String> repositories) {

    Archetype {
        blobShas = List.copyOf(blobShas);
        repositories = List.copyOf(repositories);
    }
}
