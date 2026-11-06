package io.github.fiftieshousewife.dockerfileinventory.lineage;

/** One image in an effective image's chain, with the link to the Dockerfiles that built it. */
public record ChainEntry(String configDigest, Link link) {
}
