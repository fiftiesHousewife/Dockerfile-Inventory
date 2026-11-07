package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

record Group(String key, List<String> blobShas, int locations, Set<String> packages, String family) {

    Group {
        blobShas = List.copyOf(blobShas);
        packages = Collections.unmodifiableSortedSet(new TreeSet<>(packages));
    }
}
