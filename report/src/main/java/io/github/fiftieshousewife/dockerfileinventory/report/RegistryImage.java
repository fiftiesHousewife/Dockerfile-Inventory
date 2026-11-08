package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

record RegistryImage(String configDigest, List<String> names, String parent, List<String> blobShas, int layers,
                     String created, String lastPushed, String newerBase) {

    RegistryImage {
        names = List.copyOf(names);
        blobShas = List.copyOf(blobShas);
    }
}
