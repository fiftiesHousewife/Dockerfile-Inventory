package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.Map;

record EffectiveImage(List<ChainEntry> chain, Property<String> user, Property<String> workingDir,
                      Property<List<String>> entrypoint, Property<List<String>> cmd,
                      Map<String, Property<String>> env, Map<String, Property<String>> labels,
                      boolean parentHasOnbuild) {

    EffectiveImage {
        chain = List.copyOf(chain);
        env = Map.copyOf(env);
        labels = Map.copyOf(labels);
    }
}
