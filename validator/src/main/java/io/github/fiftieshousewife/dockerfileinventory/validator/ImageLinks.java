package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

// digests: each image's names (path:tag, and repository/path:tag) to its config digest; dockerfiles: each config
// digest to the Dockerfiles lineage credits with it.
record ImageLinks(boolean lineage, Map<String, String> digests, Map<String, List<String>> dockerfiles) {

    static final ImageLinks NONE = new ImageLinks(false, Map.of(), Map.of());

    ImageLinks {
        digests = Map.copyOf(digests);
        dockerfiles = Map.copyOf(dockerfiles);
    }

    static ImageLinks read(Path lineageDir) {
        Map<String, String> digests = new HashMap<>();
        JsonLines.read(lineageDir.resolve("images.jsonl")).stream().filter(row -> row.path("configDigest").isString())
                .forEach(row -> names(row.path("repository").asString(), row.path("image").asString(),
                        row.path("tag").asString()).forEach(name -> digests.put(name,
                        row.path("configDigest").asString())));
        Map<String, List<String>> dockerfiles = JsonLines.read(lineageDir.resolve("links.jsonl")).stream()
                .filter(row -> row.path("credited").asBoolean(false))
                .collect(Collectors.toMap(row -> row.path("configDigest").asString(),
                        row -> row.path("blobShas").valueStream().map(JsonNode::asString).toList(), (a, b) -> a));
        return new ImageLinks(true, digests, dockerfiles);
    }

    static List<String> names(String repository, String image, String tag) {
        return List.of(image + ":" + tag, repository + "/" + image + ":" + tag);
    }

    ContainerLink link(Workload.Container container) {
        if (container.image().isBlank()) {
            return ContainerLink.unlinked(container, "the manifest names no image");
        }
        ImageReference reference = ImageReference.of(container.image().strip());
        if (!lineage) {
            return ContainerLink.unlinked(container, "no lineage");
        }
        if (reference.tag() == null && reference.digest() != null) {
            return ContainerLink.unlinked(container, "named by digest alone, which lineage does not keep");
        }
        String name = reference.path() + ":" + reference.tagOrLatest();
        String digest = digests.get(name);
        if (digest == null) {
            return ContainerLink.unlinked(container, "lineage read no image " + name);
        }
        List<String> credited = dockerfiles.getOrDefault(digest, List.of());
        return credited.isEmpty() ? ContainerLink.unlinked(container, "lineage credits no Dockerfile with " + name)
                : new ContainerLink(container.name(), container.image(), credited, reference.floating(), null);
    }
}
