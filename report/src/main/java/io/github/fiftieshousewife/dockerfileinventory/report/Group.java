package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dockerfiles a pass treats as one: their key, members (blob SHAs), the locations they cover, and the package set
 * and base family the near-duplicate pass compares. Members of a signature group share both, as the signature
 * holds them.
 */
public record Group(String key, List<String> blobShas, int locations, Set<String> packages, String family) {

    public Group {
        blobShas = List.copyOf(blobShas);
        packages = Collections.unmodifiableSortedSet(new TreeSet<>(packages));
    }
}
