package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

sealed interface Location permits Location.Found, Resolved.Skipped {

    // manifestDigest: the tagged manifest's or index's, as a signature names it; size: the compressed layers' bytes;
    // architectures: an index's platforms, empty for a single manifest
    record Found(String configDigest, String manifestDigest, long size, List<String> architectures)
            implements Location {

        public Found {
            architectures = List.copyOf(architectures);
        }
    }
}
