package io.github.fiftieshousewife.dockerfileinventory.lineage;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "kind")
sealed interface SetBy {

    record Image(String configDigest, Link link) implements SetBy {
    }

    record ExternalBase(String path) implements SetBy {
    }

    record Unknown() implements SetBy {
    }
}
