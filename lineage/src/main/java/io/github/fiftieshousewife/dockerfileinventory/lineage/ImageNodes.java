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
        JsonNode manifest = JSON.readTree(artifactory.manifest(repository, image, tag).orElseThrow().body());
        if (manifest.has("manifests")) {
            Optional<String> amd64 = amd64(manifest);
            if (amd64.isEmpty()) {
                return new Resolved.Skipped(Resolved.Reason.NO_AMD64, reference(repository, image, tag));
            }
            String entry = amd64.get();
            manifest = JSON.readTree(artifactory.manifest(repository, image, entry).orElseThrow().body());
        }
        String configDigest = manifest.path("config").path("digest").asString();
        String config = artifactory.blob(repository, image, configDigest).orElseThrow();
        return new Resolved.Node(configDigest, ImageConfig.parse(config).orElseThrow());
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
