package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

record RegistryImage(String configDigest, List<String> names, String parent, List<String> blobShas, int layers,
                     String created, String lastPushed, String newerBase, Map<String, ImageLabel> labels,
                     List<String> candidates) {

    RegistryImage {
        names = List.copyOf(names);
        blobShas = List.copyOf(blobShas);
        labels = Collections.unmodifiableMap(new TreeMap<>(labels));
        candidates = List.copyOf(candidates);
    }
}
