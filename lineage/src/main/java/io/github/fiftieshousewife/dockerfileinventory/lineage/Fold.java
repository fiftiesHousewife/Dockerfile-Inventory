package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Folds each image's chain into its effective properties, attributing each to the image that set it. */
public final class Fold {

    private Fold() {
    }

    public static Map<String, EffectiveImage> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                                 Map<String, Link> links) {
        Map<String, EffectiveImage> effective = new HashMap<>();
        for (Resolved.Node image : images) {
            List<Instruction> own = OwnSteps.of(image, null);
            SetBy user = own.stream().anyMatch(i -> i.keyword().equals("USER"))
                    ? new SetBy.Image(image.configDigest(), links.get(image.configDigest()))
                    : new SetBy.Unknown();
            effective.put(image.configDigest(), new EffectiveImage(new Property<>(image.config().user(), user)));
        }
        return effective;
    }
}
