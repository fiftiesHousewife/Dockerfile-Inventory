package io.github.fiftieshousewife.dockerfileinventory.lineage;

/** An image's effective runtime properties, each with the image in its chain that set it. */
public record EffectiveImage(Property<String> user) {
}
