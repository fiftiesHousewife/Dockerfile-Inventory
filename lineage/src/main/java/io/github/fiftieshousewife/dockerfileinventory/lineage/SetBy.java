package io.github.fiftieshousewife.dockerfileinventory.lineage;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Who set an effective property. */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "kind")
public sealed interface SetBy {

    /** An image in the chain, by its own steps; its link names the Dockerfile to change. */
    record Image(String configDigest, Link link) implements SetBy {
    }

    /** The base a root was built from, outside Artifactory, as Buildah's {@code FROM} comment names it. */
    record ExternalBase(String path) implements SetBy {
    }

    /** No recorded step sets it: a squashed image, or history the builder did not write. */
    record Unknown() implements SetBy {
    }
}
