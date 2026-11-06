package io.github.fiftieshousewife.dockerfileinventory.lineage;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/** Where an image sits in the family tree. */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "kind")
public sealed interface Parent {

    /** No other image's layers start this one's: a mirrored external base, or an image whose base is missing. */
    record Root() implements Parent {
    }

    /** The image this one was built on. */
    record Of(String configDigest) implements Parent {
    }

    /** Several images fit equally well (Q11); links and the fold use the earliest pushed. */
    record Ambiguous(List<String> configDigests) implements Parent {

        public Ambiguous {
            configDigests = List.copyOf(configDigests);
        }
    }
}
