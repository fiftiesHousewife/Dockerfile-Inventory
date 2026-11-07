package io.github.fiftieshousewife.dockerfileinventory.lineage;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "kind")
sealed interface Parent {

    record Root() implements Parent {
    }

    record Of(String configDigest) implements Parent {
    }

    record Ambiguous(List<String> configDigests) implements Parent {

        public Ambiguous {
            configDigests = List.copyOf(configDigests);
        }
    }

    // An ambiguous parent's candidates share layers and history, so any of them gives the same own steps.
    static Resolved.Node node(Parent parent, Map<String, Resolved.Node> byDigest) {
        return switch (parent) {
            case Of of -> byDigest.get(of.configDigest());
            case Ambiguous ambiguous -> byDigest.get(ambiguous.configDigests().getFirst());
            case Root root -> null;
            case null -> null;
        };
    }
}
