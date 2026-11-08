package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

final class ConfigDifferences {

    // Written by the builder at build time, so two builds of one Dockerfile differ in them.
    static final Set<String> BUILD_TIME_LABELS = Set.of("io.buildah.version", "org.opencontainers.image.created");
    private static final String LABELS = "Labels";

    private ConfigDifferences() {
    }

    static List<String> of(JsonNode original, JsonNode regenerated) {
        return names(original, regenerated).stream()
                .flatMap(name -> name.equals(LABELS)
                        ? labels(original.path(LABELS), regenerated.path(LABELS))
                        : difference(name + ": ", original.get(name), regenerated.get(name)).stream())
                .toList();
    }

    static Stream<String> labels(JsonNode original, JsonNode regenerated) {
        return names(original, regenerated).stream()
                .filter(label -> !BUILD_TIME_LABELS.contains(label))
                .flatMap(label -> difference("label " + label + ": ", original.get(label), regenerated.get(label))
                        .stream());
    }

    static Optional<String> difference(String what, JsonNode original, JsonNode regenerated) {
        return Objects.equals(original, regenerated) ? Optional.empty()
                : Optional.of(what + text(original) + " became " + text(regenerated));
    }

    private static Set<String> names(JsonNode original, JsonNode regenerated) {
        Set<String> names = new TreeSet<>(original.propertyNames());
        names.addAll(regenerated.propertyNames());
        return names;
    }

    private static String text(JsonNode node) {
        return node == null ? "(none)" : node.toString();
    }
}
