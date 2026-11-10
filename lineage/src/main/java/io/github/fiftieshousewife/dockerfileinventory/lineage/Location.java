package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

sealed interface Location permits Location.Found, Resolved.Skipped {

    record Found(String configDigest, String manifestDigest, long size, List<String> architectures)
            implements Location {

        public Found {
            architectures = List.copyOf(architectures);
        }
    }
}
