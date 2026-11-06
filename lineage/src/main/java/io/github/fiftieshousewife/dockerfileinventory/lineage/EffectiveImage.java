package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.Map;

/** An image's effective runtime properties, each with the image in its chain that set it. */
public record EffectiveImage(Property<String> user, Property<String> workingDir, Property<List<String>> entrypoint,
                             Property<List<String>> cmd, Map<String, Property<String>> env,
                             Map<String, Property<String>> labels) {

    public EffectiveImage {
        env = Map.copyOf(env);
        labels = Map.copyOf(labels);
    }
}
