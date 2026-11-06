package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.function.Function;

/** A rule about an image's inherited properties (D5): it names the Dockerfile in the chain that should change. */
public interface EffectiveRule {

    String id();

    Severity severity();

    /** {@code blobText} gives a linked Dockerfile's text by blob SHA, or null when it was not fetched. */
    List<Finding> check(ImageView image, Function<String, String> blobText);
}
