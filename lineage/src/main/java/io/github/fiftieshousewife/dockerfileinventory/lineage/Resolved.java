package io.github.fiftieshousewife.dockerfileinventory.lineage;

sealed interface Resolved {

    record Node(String configDigest, ImageConfig config) implements Resolved {
    }

    record Skipped(Reason reason, String detail) implements Resolved, Location {
    }

    enum Reason {
        NO_AMD64,
        SCHEMA_1,
        GONE,
        UNREADABLE_CONFIG
    }
}
