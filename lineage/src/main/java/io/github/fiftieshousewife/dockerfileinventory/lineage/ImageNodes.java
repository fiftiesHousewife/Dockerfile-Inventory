package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class ImageNodes {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageNodes() {
    }

    static Resolved resolve(Artifactory artifactory, String repository, String image, String tag) {
        return switch (locate(artifactory, repository, image, tag)) {
            case Location.Found found -> node(artifactory, repository, image, found.configDigest());
            case Resolved.Skipped skipped -> skipped;
        };
    }

    static Location locate(Artifactory artifactory, String repository, String image, String tag) {
        String reference = repository + "/" + image + ":" + tag;
        Optional<Manifest> tagged = artifactory.manifest(repository, image, tag);
        if (tagged.isEmpty()) {
            return new Resolved.Skipped(Resolved.Reason.GONE, reference);
        }
        JsonNode manifest = JSON.readTree(tagged.get().body());
        String digest = tagged.get().digest();
        if (manifest.path("schemaVersion").asInt() == 1) {
            return new Resolved.Skipped(Resolved.Reason.SCHEMA_1, reference);
        }
        return manifest.has("manifests") ? throughList(artifactory, repository, image, manifest, digest, reference)
                : found(manifest, digest, List.of());
    }

    static Location throughList(Artifactory artifactory, String repository, String image, JsonNode list,
                                String listDigest, String reference) {
        Optional<String> amd64 = amd64(list);
        if (amd64.isEmpty()) {
            return new Resolved.Skipped(Resolved.Reason.NO_AMD64, reference);
        }
        List<String> architectures = architectures(list);
        return manifest(artifactory, repository, image, amd64.get())
                .<Location>map(manifest -> found(manifest, listDigest, architectures))
                .orElseGet(() -> new Resolved.Skipped(Resolved.Reason.GONE, amd64.get()));
    }

    static Location found(JsonNode manifest, String manifestDigest, List<String> architectures) {
        long size = StreamSupport.stream(manifest.path("layers").spliterator(), false)
                .mapToLong(layer -> layer.path("size").asLong()).sum();
        return new Location.Found(manifest.path("config").path("digest").asString(), manifestDigest, size,
                architectures);
    }

    static List<String> architectures(JsonNode list) {
        return StreamSupport.stream(list.path("manifests").spliterator(), false)
                .map(m -> m.path("platform"))
                .filter(platform -> !platform.path("os").asString().equals("unknown"))
                .map(platform -> platform.path("os").asString() + "/" + platform.path("architecture").asString()
                        + (platform.path("variant").isString() ? "/" + platform.path("variant").asString() : ""))
                .toList();
    }

    static Resolved node(Artifactory artifactory, String repository, String image, String configDigest) {
        Optional<String> text = artifactory.blob(repository, image, configDigest);
        if (text.isEmpty()) {
            return new Resolved.Skipped(Resolved.Reason.GONE, configDigest);
        }
        return ImageConfig.parse(text.get())
                .<Resolved>map(config -> new Resolved.Node(configDigest, config))
                .orElseGet(() -> new Resolved.Skipped(Resolved.Reason.UNREADABLE_CONFIG, configDigest));
    }

    private static Optional<JsonNode> manifest(Artifactory artifactory, String repository, String image, String ref) {
        return artifactory.manifest(repository, image, ref).map(m -> JSON.readTree(m.body()));
    }

    static Optional<String> amd64(JsonNode list) {
        return StreamSupport.stream(list.path("manifests").spliterator(), false)
                .filter(m -> m.path("platform").path("os").asString().equals("linux"))
                .filter(m -> m.path("platform").path("architecture").asString().equals("amd64"))
                .map(m -> m.path("digest").asString())
                .findFirst();
    }
}
