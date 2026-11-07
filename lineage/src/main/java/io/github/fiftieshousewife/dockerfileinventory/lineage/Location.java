package io.github.fiftieshousewife.dockerfileinventory.lineage;

sealed interface Location permits Location.Found, Resolved.Skipped {

    record Found(String configDigest) implements Location {
    }
}
