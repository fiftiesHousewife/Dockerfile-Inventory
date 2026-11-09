package io.github.fiftieshousewife.dockerfileinventory.report;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

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
    private final Map<String, List<RegistryImage>> builtFrom;
    private final Map<String, List<String>> candidatesOf;
    private final Map<String, List<String>> childrenOf;
    private final int layerLimit;
    private final ImageChains chains;
    private final Cadences cadences;

    Hierarchies(Map<String, RegistryImage> images, int staleAfterMonths, int layerLimit) {
        this(images, staleAfterMonths, layerLimit, Map.of());
    }

    Hierarchies(Map<String, RegistryImage> images, int staleAfterMonths, int layerLimit,
                Map<String, List<Instant>> pushesByName) {
        this.images = Map.copyOf(images);
        this.layerLimit = layerLimit;
        String newest = RegistryImages.newestPush(List.copyOf(images.values()));
        Instant newestPush = newest == null ? Instant.EPOCH : Instant.parse(newest);
        this.chains = new ImageChains(images, newestPush, staleAfterMonths);
        this.cadences = new Cadences(images, pushesByName, newestPush);
        this.builtFrom = bySha(images.values(), RegistryImage::blobShas, image -> image);
        this.candidatesOf = bySha(images.values(), RegistryImage::candidates, RegistryImage::configDigest);
        this.childrenOf = childrenOf(images.values());
    }

    static <T> Map<String, List<T>> bySha(Collection<RegistryImage> images,
                                          Function<RegistryImage, List<String>> shas,
                                          Function<RegistryImage, T> value) {
        return images.stream()
                .flatMap(image -> shas.apply(image).stream().map(sha -> Map.entry(sha, value.apply(image))))
                .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));
    }

    static Map<String, List<String>> childrenOf(Collection<RegistryImage> images) {
        return images.stream().filter(image -> image.parent() != null)
                .collect(groupingBy(RegistryImage::parent, mapping(RegistryImage::configDigest, toList())));
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
                .filter(image -> image.lastPushed() != null && chains.ownLayers(image) != null)
                .max(Comparator.comparing(image -> Instant.parse(image.lastPushed())));
    }

    Layers imageLayers(RegistryImage image) {
        int inherited = chains.parentLayers(image);
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
        List<Step> chain = chains.chain(configDigest);
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

    int imagesBelow(String blobSha) {
        Set<String> below = new HashSet<>();
        builtFrom.getOrDefault(blobSha, List.of()).forEach(image -> addDescendants(image.configDigest(), below));
        return below.size();
    }

    void addDescendants(String configDigest, Set<String> below) {
        childrenOf.getOrDefault(configDigest, List.of()).stream().filter(below::add)
                .forEach(child -> addDescendants(child, below));
    }

    Layers limited(int own, int inherited, String counted) {
        return new Layers(own, inherited, own + inherited, counted, own + inherited > layerLimit);
    }
}
