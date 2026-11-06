package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

/** An image's effective runtime properties, each with the image in its chain that set it. */
public record EffectiveImage(Property<String> user, Property<String> workingDir, Property<List<String>> entrypoint,
                             Property<List<String>> cmd) {
}
