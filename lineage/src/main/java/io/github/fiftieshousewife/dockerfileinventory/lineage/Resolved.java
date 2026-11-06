package io.github.fiftieshousewife.dockerfileinventory.lineage;

/** One tag resolved to the image it names, or the reason it was not. */
public sealed interface Resolved {

    /** An image, identified by its config digest: tags and repositories holding the same build share it. */
    record Node(String configDigest, ImageConfig config) implements Resolved {
    }

    /** A tag lineage does not read, counted in the rates. {@code detail} names the digest or media type. */
    record Skipped(Reason reason, String detail) implements Resolved, Location {
    }

    enum Reason {
        NO_AMD64,
        SCHEMA_1,
        GONE,
        UNREADABLE_CONFIG
    }
}
