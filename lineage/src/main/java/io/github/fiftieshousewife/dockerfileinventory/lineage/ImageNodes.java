package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Optional;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Resolves one tag to its image: manifest, then the amd64 entry of a list, then the config. */
public final class ImageNodes {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageNodes() {
    }

    public static Resolved resolve(Artifactory artifactory, String repository, String image, String tag) {
        String reference = reference(repository, image, tag);
        Optional<JsonNode> tagged = manifest(artifactory, repository, image, tag);
        if (tagged.isEmpty()) {
            return new Resolved.Skipped(Resolved.Reason.GONE, reference);
        }
        JsonNode manifest = tagged.get();
        if (manifest.path("schemaVersion").asInt() == 1) {
            return new Resolved.Skipped(Resolved.Reason.SCHEMA_1, reference);
        }
        if (manifest.has("manifests")) {
            Optional<String> amd64 = amd64(manifest);
            if (amd64.isEmpty()) {
                return new Resolved.Skipped(Resolved.Reason.NO_AMD64, reference);
            }
            Optional<JsonNode> entry = manifest(artifactory, repository, image, amd64.get());
            if (entry.isEmpty()) {
                return new Resolved.Skipped(Resolved.Reason.GONE, amd64.get());
            }
            manifest = entry.get();
        }
        String configDigest = manifest.path("config").path("digest").asString();
        return artifactory.blob(repository, image, configDigest)
                .<Resolved>map(config -> new Resolved.Node(configDigest, ImageConfig.parse(config).orElseThrow()))
                .orElseGet(() -> new Resolved.Skipped(Resolved.Reason.GONE, configDigest));
    }

    private static Optional<JsonNode> manifest(Artifactory artifactory, String repository, String image, String ref) {
        return artifactory.manifest(repository, image, ref).map(m -> JSON.readTree(m.body()));
    }

    private static String reference(String repository, String image, String tag) {
        return repository + "/" + image + ":" + tag;
    }

    /** The digest of a manifest list's {@code linux/amd64} entry (Q3: one platform only). */
    static Optional<String> amd64(JsonNode list) {
        return StreamSupport.stream(list.path("manifests").spliterator(), false)
                .filter(m -> m.path("platform").path("os").asString().equals("linux"))
                .filter(m -> m.path("platform").path("architecture").asString().equals("amd64"))
                .map(m -> m.path("digest").asString())
                .findFirst();
    }
}
