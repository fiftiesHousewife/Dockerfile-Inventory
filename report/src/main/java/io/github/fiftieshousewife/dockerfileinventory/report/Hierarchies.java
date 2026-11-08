package io.github.fiftieshousewife.dockerfileinventory.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class Hierarchies {

    record Hierarchy(Layers layers, List<BuiltImage> images, List<String> unmaintained) {
    }

    record Layers(int own, int inherited, int total, String counted, boolean overLimit) {
    }

    record BuiltImage(String configDigest, List<String> names, String created, String lastPushed, List<Step> chain,
                      List<Step> blockedBy, Map<String, Label> labels) {
    }

    record Label(String value, String source, String setBy) {
    }

    record Step(String configDigest, List<String> names, List<String> blobShas, int ownLayers, String created,
                String lastPushed, List<String> unmaintained) {
    }

    private final Map<String, RegistryImage> images;
    private final Map<String, List<RegistryImage>> builtFrom = new HashMap<>();
    private final Instant newestPush;
    private final int staleAfterMonths;
    private final int layerLimit;

    Hierarchies(Map<String, RegistryImage> images, int staleAfterMonths, int layerLimit) {
        this.images = Map.copyOf(images);
        this.staleAfterMonths = staleAfterMonths;
        this.layerLimit = layerLimit;
        String newest = RegistryImages.newestPush(List.copyOf(images.values()));
        this.newestPush = newest == null ? Instant.EPOCH : Instant.parse(newest);
        images.values().forEach(image -> image.blobShas().forEach(sha ->
                builtFrom.computeIfAbsent(sha, key -> new ArrayList<>()).add(image)));
    }

    Hierarchy of(CatalogueEntry entry, List<CatalogueEntry> entries, Map<String, CatalogueLinks.Links> links,
                 Set<String> archivedRepositories) {
        List<BuiltImage> built = builtFrom.getOrDefault(entry.blobSha(), List.of()).stream()
                .sorted(Comparator.comparing(RegistryImage::configDigest))
                .map(image -> built(image.configDigest(), entry.image().labels().keySet())).toList();
        return new Hierarchy(layers(entry, entries, links), built,
                Unmaintained.archived(entry.locations(), archivedRepositories));
    }

    Layers layers(CatalogueEntry entry, List<CatalogueEntry> entries, Map<String, CatalogueLinks.Links> links) {
        return newestImage(entry.blobSha()).map(this::imageLayers)
                .orElseGet(() -> declaredLayers(entry, entries, links));
    }

    Optional<RegistryImage> newestImage(String blobSha) {
        return builtFrom.getOrDefault(blobSha, List.of()).stream().filter(image -> image.lastPushed() != null)
                .max(Comparator.comparing(image -> Instant.parse(image.lastPushed())));
    }

    Layers imageLayers(RegistryImage image) {
        int inherited = parentLayers(image);
        return limited(image.layers() - inherited, inherited, "image");
    }

    Layers declaredLayers(CatalogueEntry entry, List<CatalogueEntry> entries,
                          Map<String, CatalogueLinks.Links> links) {
        Map<String, CatalogueEntry> bySha = new HashMap<>();
        entries.forEach(e -> bySha.put(e.blobSha(), e));
        int inherited = Footprint.through(entry.blobSha(), entries, links).through().stream().map(bySha::get)
                .filter(ancestor -> ancestor.build() != null).mapToInt(ancestor -> ancestor.build().layers()).sum();
        return limited(entry.build().layers(), inherited, "declared");
    }

    BuiltImage built(String configDigest, Set<String> dockerfileLabels) {
        RegistryImage image = images.get(configDigest);
        List<Step> chain = chain(configDigest);
        Map<String, Label> labels = new TreeMap<>();
        image.labels().forEach((name, label) -> labels.put(name, label(name, label, configDigest, dockerfileLabels)));
        return new BuiltImage(configDigest, image.names(), image.created(), image.lastPushed(), chain,
                chain.stream().skip(1).filter(step -> !step.unmaintained().isEmpty()).toList(), labels);
    }

    static Label label(String name, ImageLabel label, String configDigest, Set<String> dockerfileLabels) {
        if (label.setByBase() != null) {
            return new Label(label.value(), "base", label.setByBase());
        }
        if (label.setByImage() == null) {
            return new Label(label.value(), "unknown", null);
        }
        if (!label.setByImage().equals(configDigest)) {
            return new Label(label.value(), "ancestor", label.setByImage());
        }
        return new Label(label.value(), dockerfileLabels.contains(name) ? "dockerfile" : "build", configDigest);
    }

    List<Step> chain(String configDigest) {
        List<Step> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RegistryImage image = images.get(configDigest); image != null && seen.add(image.configDigest());
                image = image.parent() == null ? null : images.get(image.parent())) {
            chain.add(new Step(image.configDigest(), image.names(), image.blobShas(),
                    image.layers() - parentLayers(image), image.created(), image.lastPushed(),
                    Unmaintained.reasons(image, newestPush, staleAfterMonths)));
        }
        return chain;
    }

    int parentLayers(RegistryImage image) {
        RegistryImage parent = image.parent() == null ? null : images.get(image.parent());
        return parent == null ? 0 : parent.layers();
    }

    private Layers limited(int own, int inherited, String counted) {
        return new Layers(own, inherited, own + inherited, counted, own + inherited > layerLimit);
    }
}
