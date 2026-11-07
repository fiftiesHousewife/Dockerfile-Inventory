package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

record EffectiveImage(List<ChainEntry> chain, Property<String> user, Property<String> workingDir,
                      Property<List<String>> entrypoint, Property<List<String>> cmd,
                      Map<String, Property<String>> env, Map<String, Property<String>> labels,
                      boolean parentHasOnbuild) {

    EffectiveImage {
        chain = List.copyOf(chain);
        env = Collections.unmodifiableMap(new TreeMap<>(env));
        labels = Collections.unmodifiableMap(new TreeMap<>(labels));
    }
}
