package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** The image family tree, rebuilt from layers alone. */
public final class Lineage {

    private Lineage() {
    }

    public static Map<String, Parent> of(Collection<Resolved.Node> nodes) {
        return nodes.stream().collect(Collectors.toMap(Resolved.Node::configDigest, n -> new Parent.Root()));
    }
}
