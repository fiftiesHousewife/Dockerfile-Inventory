package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.Map;

/**
 * An image's effective runtime properties, each with the image in its chain that set it. {@code parentHasOnbuild}
 * marks a child whose own steps begin with its parent's triggers, which its Dockerfile does not have (Q18).
 */
public record EffectiveImage(List<ChainEntry> chain, Property<String> user, Property<String> workingDir,
                             Property<List<String>> entrypoint, Property<List<String>> cmd,
                             Map<String, Property<String>> env, Map<String, Property<String>> labels,
                             boolean parentHasOnbuild) {

    public EffectiveImage {
        chain = List.copyOf(chain);
        env = Map.copyOf(env);
        labels = Map.copyOf(labels);
    }
}
