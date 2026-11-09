package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

record Catalogue(Map<String, String> archetypes, Map<String, String> representatives,
                 Map<String, List<String>> builtOn) {

    static final Catalogue NONE = new Catalogue(Map.of(), Map.of(), Map.of());

    // The ranking's groups of files that are not alike: the report's Passes.PARSE_FAILURES and Passes.TEMPLATES.
    static final Set<String> NOT_ARCHETYPES = Set.of("parse-failures", "templates");

    static Catalogue read(Path dir) {
        Map<String, String> archetypes = new HashMap<>();
        Map<String, String> representatives = new HashMap<>();
        JsonLines.read(dir.resolve("archetypes.jsonl")).stream()
                .filter(row -> !NOT_ARCHETYPES.contains(row.path("key").asString())).forEach(row -> {
            String key = row.path("key").asString();
            representatives.put(key, row.path("representative").asString());
            row.path("blobShas").forEach(sha -> archetypes.put(sha.asString(), key));
        });
        Map<String, List<String>> builtOn = new HashMap<>();
        JsonLines.read(dir.resolve("catalogue/dockerfiles.jsonl")).forEach(row -> builtOn.put(
                row.path("blobSha").asString(), row.path("links").path("children").valueStream()
                        .map(child -> child.path("blobSha").asString()).toList()));
        return new Catalogue(archetypes, representatives, builtOn);
    }

    String archetype(String blobSha) {
        return archetypes.getOrDefault(blobSha, blobSha);
    }

    String representative(String archetype) {
        return representatives.get(archetype);
    }

    List<String> children(String blobSha) {
        return builtOn.getOrDefault(blobSha, List.of());
    }
}
