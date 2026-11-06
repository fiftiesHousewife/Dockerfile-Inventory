package io.github.fiftieshousewife.dockerfileinventory.lineage;

import tools.jackson.databind.ObjectMapper;

/** Resolves one tag to its image: manifest, then the amd64 entry of a list, then the config. */
public final class ImageNodes {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageNodes() {
    }

    public static Resolved resolve(Artifactory artifactory, String repository, String image, String tag) {
        Manifest manifest = artifactory.manifest(repository, image, tag).orElseThrow();
        String configDigest = JSON.readTree(manifest.body()).path("config").path("digest").asString();
        String config = artifactory.blob(repository, image, configDigest).orElseThrow();
        return new Resolved.Node(configDigest, ImageConfig.parse(config).orElseThrow());
    }
}
