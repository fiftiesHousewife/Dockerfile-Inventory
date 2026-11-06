package io.github.fiftieshousewife.dockerfileinventory.lineage;

/**
 * A manifest as served: its media type says whether it is one image or a list of platforms, and its digest is the
 * identity a manifest list's entries refer to.
 */
public record Manifest(String mediaType, String digest, String body) {
}
