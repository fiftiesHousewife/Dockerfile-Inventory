package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

record Profile(String name, String describes, List<String> allowedRegistries, Map<Check, Severity> severity,
               List<String> allowedCapabilities) {

    static final String DEFAULT = "openshift-4-restricted-v2";
    static final List<String> SHIPPED = List.of(DEFAULT, "openshift-4-restricted");

    Profile {
        allowedRegistries = List.copyOf(allowedRegistries);
        severity = Map.copyOf(severity);
        allowedCapabilities = List.copyOf(allowedCapabilities);
    }

    static Profile of(String setting) {
        if (SHIPPED.contains(setting)) {
            return shipped(setting);
        }
        Path file = Path.of(setting);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("PROFILE " + setting + " is neither a shipped profile ("
                    + String.join(", ", SHIPPED) + ") nor a file");
        }
        try {
            return parse(setting, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Profile shipped(String name) {
        try (InputStream in = Profile.class.getResourceAsStream("/profiles/" + name + ".json")) {
            return parse(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Profile parse(String source, String json) {
        JsonNode root = new ObjectMapper().readTree(json);
        Map<Check, Severity> severity = new EnumMap<>(Check.class);
        root.path("severity").properties().forEach(e -> severity.put(check(source, e.getKey()),
                severity(source, e.getKey(), e.getValue().asString())));
        root.path("pod").properties().forEach(e -> severity.put(pod(source, e.getKey()),
                severity(source, e.getKey(), e.getValue().asString())));
        Arrays.stream(Check.values()).filter(c -> !c.rule() && !severity.containsKey(c)).findFirst()
                .ifPresent(missing -> {
                    throw refused(source, "no severity for " + missing);
                });
        Arrays.stream(Check.values()).filter(Check::rule).forEach(rule -> severity.put(rule, Severity.INFORMATION));
        root.path("rules").properties().forEach(e -> severity.put(rule(source, e.getKey()),
                severity(source, e.getKey(), e.getValue().asString())));
        return new Profile(root.path("name").asString(), root.path("describes").asString(),
                strings(root.path("allowedRegistries")), severity, strings(root.path("allowedCapabilities")));
    }

    private static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }

    static Check check(String source, String id) {
        return platform().filter(c -> c.name().equals(id)).findFirst()
                .orElseThrow(() -> refused(source, id + " is not a check (P1 to P" + platform().count() + ")"));
    }

    static Check pod(String source, String id) {
        return Arrays.stream(Check.values()).filter(c -> c.pod() && c.name().equals(id)).findFirst()
                .orElseThrow(() -> refused(source, id + " is not a pod check (D1 to D6)"));
    }

    static Check rule(String source, String id) {
        return Arrays.stream(Check.values()).filter(c -> c.rule() && c.name().equals(id)).findFirst()
                .orElseThrow(() -> refused(source, id + " is not a rule"));
    }

    static Stream<Check> platform() {
        return Arrays.stream(Check.values()).filter(Check::platform);
    }

    static Severity severity(String source, String check, String word) {
        return Arrays.stream(Severity.values()).filter(s -> s.word().equals(word.toLowerCase(Locale.ROOT)))
                .findFirst().orElseThrow(() -> refused(source, check + " is " + word + ", not " + words()));
    }

    static String words() {
        List<String> words = Arrays.stream(Severity.values()).map(Severity::word).toList();
        return words.subList(0, words.size() - 1).stream().collect(Collectors.joining(", ")) + " or " + words.getLast();
    }

    private static IllegalArgumentException refused(String source, String why) {
        return new IllegalArgumentException("profile " + source + ": " + why);
    }
}
