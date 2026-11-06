package io.github.fiftieshousewife.dockerfileinventory.lineage;

/** Where a tag's config is, found from its manifest before the config itself is fetched; or why it is not read. */
public sealed interface Location permits Location.Found, Resolved.Skipped {

    record Found(String configDigest) implements Location {
    }
}
