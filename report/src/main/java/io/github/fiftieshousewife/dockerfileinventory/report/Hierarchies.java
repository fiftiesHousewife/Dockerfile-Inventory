package io.github.fiftieshousewife.dockerfileinventory.report;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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

    record Hierarchy(Layers layers, List<BuiltImage> images, List<String> candidateImages, List<String> unmaintained) {
    }

    record Layers(int own, int inherited, int total, String counted, boolean overLimit) {
    }

    record BuiltImage(String configDigest, List<String> names, String created, String lastPushed, List<Step> chain,
                      List<Step> blockedBy, Map<String, Label> labels, Long size, List<String> architectures,
                      Cadences.Cadence cadence, Provenance provenance, RegistryImage.Usage usage) {
    }

    record Label(String value, String source, String setBy) {
    }

    record Step(String configDigest, List<String> names, List<String> blobShas, Integer ownLayers, String created,
                String lastPushed, List<String> unmaintained) {
    }

    private final Map<String, RegistryImage> images;
    private final Map<String, List<RegistryImage>> builtFrom = new HashMap<>();
    private final Map<String, List<String>> candidatesOf = new HashMap<>();
    private final Map<String, List<String>> childrenOf = new HashMap<>();
    private final Instant newestPush;
    private final int staleAfterMonths;
    private final int layerLimit;
    private final Cadences cadences;

    Hierarchies(Map<String, RegistryImage> images, int staleAfterMonths, int layerLimit) {
        this(images, staleAfterMonths, layerLimit, Map.of());
    }

    Hierarchies(Map<String, RegistryImage> images, int staleAfterMonths, int layerLimit,
                Map<String, List<Instant>> pushesByName) {
        this.images = Map.copyOf(images);
        this.staleAfterMonths = staleAfterMonths;
        this.layerLimit = layerLimit;
        String newest = RegistryImages.newestPush(List.copyOf(images.values()));
        this.newestPush = newest == null ? Instant.EPOCH : Instant.parse(newest);
        this.cadences = new Cadences(images, pushesByName, newestPush);
        images.values().forEach(image -> {
            image.blobShas().forEach(sha -> builtFrom.computeIfAbsent(sha, key -> new ArrayList<>()).add(image));
            image.candidates().forEach(sha -> candidatesOf.computeIfAbsent(sha, key -> new ArrayList<>())
                    .add(image.configDigest()));
            if (image.parent() != null) {
                childrenOf.computeIfAbsent(image.parent(), key -> new ArrayList<>()).add(image.configDigest());
            }
        });
    }

    Hierarchy of(CatalogueEntry entry, Map<String, CatalogueEntry> bySha, List<String> ancestors,
                 Set<String> archivedRepositories) {
        List<BuiltImage> built = builtFrom.getOrDefault(entry.blobSha(), List.of()).stream()
                .sorted(Comparator.comparing(RegistryImage::configDigest))
                .map(image -> built(image.configDigest(), entry.image().labels().keySet())).toList();
        List<String> candidates = candidatesOf.getOrDefault(entry.blobSha(), List.of()).stream().sorted().toList();
        return new Hierarchy(layers(entry, bySha, ancestors), built, candidates,
                Unmaintained.archived(entry.locations(), archivedRepositories));
    }

    Layers layers(CatalogueEntry entry, Map<String, CatalogueEntry> bySha, List<String> ancestors) {
        return newestImage(entry.blobSha()).map(this::imageLayers)
                .orElseGet(() -> declaredLayers(entry, bySha, ancestors));
    }

    Optional<RegistryImage> newestImage(String blobSha) {
        return builtFrom.getOrDefault(blobSha, List.of()).stream()
                .filter(image -> image.lastPushed() != null && ownLayers(image) != null)
                .max(Comparator.comparing(image -> Instant.parse(image.lastPushed())));
    }

    Layers imageLayers(RegistryImage image) {
        int inherited = parentLayers(image);
        return limited(image.layers() - inherited, inherited, "image");
    }

    Layers declaredLayers(CatalogueEntry entry, Map<String, CatalogueEntry> bySha, List<String> ancestors) {
        int inherited = ancestors.stream().map(bySha::get)
                .filter(ancestor -> ancestor != null && ancestor.build() != null)
                .mapToInt(ancestor -> ancestor.build().layers()).sum();
        return limited(entry.build().layers(), inherited, "declared");
    }

    BuiltImage built(String configDigest, Set<String> dockerfileLabels) {
        RegistryImage image = images.get(configDigest);
        List<Step> chain = chain(configDigest);
        Map<String, Label> labels = new TreeMap<>();
        image.labels().forEach((name, label) -> labels.put(name, label(name, label, configDigest, dockerfileLabels)));
        return new BuiltImage(configDigest, image.names(), image.created(), image.lastPushed(), chain,
                chain.stream().skip(1).filter(step -> !step.unmaintained().isEmpty()).toList(), labels, image.size(),
                image.architectures(), cadences.of(configDigest), image.provenance(), image.usage());
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
                    ownLayers(image), image.created(), image.lastPushed(),
                    Unmaintained.reasons(image, newestPush, staleAfterMonths)));
        }
        return chain;
    }

    int imagesBelow(String blobSha) {
        Set<String> below = new HashSet<>();
        Deque<String> next = new ArrayDeque<>();
        builtFrom.getOrDefault(blobSha, List.of()).forEach(image -> next.push(image.configDigest()));
        while (!next.isEmpty()) {
            childrenOf.getOrDefault(next.pop(), List.of()).stream().filter(below::add).forEach(next::push);
        }
        return below.size();
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

    Layers limited(int own, int inherited, String counted) {
        return new Layers(own, inherited, own + inherited, counted, own + inherited > layerLimit);
    }
}
