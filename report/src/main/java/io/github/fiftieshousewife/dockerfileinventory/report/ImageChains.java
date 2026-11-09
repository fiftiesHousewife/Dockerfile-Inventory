package io.github.fiftieshousewife.dockerfileinventory.report;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class ImageChains {

    private final Map<String, RegistryImage> images;
    private final Instant newestPush;
    private final int staleAfterMonths;

    ImageChains(Map<String, RegistryImage> images, Instant newestPush, int staleAfterMonths) {
        this.images = Map.copyOf(images);
        this.newestPush = newestPush;
        this.staleAfterMonths = staleAfterMonths;
    }

    List<Hierarchies.Step> chain(String configDigest) {
        Set<String> seen = new HashSet<>();
        return Stream.iterate(images.get(configDigest), image -> image != null && seen.add(image.configDigest()),
                this::parentOf).map(this::step).toList();
    }

    RegistryImage parentOf(RegistryImage image) {
        return image.parent() == null ? null : images.get(image.parent());
    }

    Hierarchies.Step step(RegistryImage image) {
        return new Hierarchies.Step(image.configDigest(), image.names(), image.blobShas(), ownLayers(image),
                image.created(), image.lastPushed(), Unmaintained.reasons(image, newestPush, staleAfterMonths));
    }

    Integer parentLayers(RegistryImage image) {
        if (image.parent() == null) {
            return 0;
        }
        RegistryImage parent = images.get(image.parent());
        return parent == null ? null : parent.layers();
    }

    Integer ownLayers(RegistryImage image) {
        Integer inherited = parentLayers(image);
        return image.layers() == null || inherited == null ? null : image.layers() - inherited;
    }
}
