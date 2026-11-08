package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

// The Dockerfiles the image round trip builds, as a file lists them: one per line, a blob SHA or "owner/repository
// path/to/Dockerfile", "#" starting a comment; each found in discovery's manifest.
final class DockerfileSelection {

    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");

    private DockerfileSelection() {
    }

    static List<String> shas(List<String> lines, List<JsonNode> manifest) {
        return lines.stream()
                .map(line -> line.contains("#") ? line.substring(0, line.indexOf('#')) : line)
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> sha(line, manifest))
                .distinct()
                .toList();
    }

    static String sha(String line, List<JsonNode> manifest) {
        String[] parts = line.split("\\s+", 2);
        return manifest.stream()
                .filter(row -> SHA.matcher(line).matches() ? row.path("blobSha").asString().equals(line)
                        : parts.length == 2 && row.path("repository").asString().equals(parts[0])
                                && row.path("path").asString().equals(parts[1]))
                .map(row -> row.path("blobSha").asString())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no Dockerfile in the manifest for: " + line));
    }
}
