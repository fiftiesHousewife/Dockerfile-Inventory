package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Manifests and blobs held in memory, keyed as Artifactory addresses them. */
final class FakeArtifactory implements Artifactory {

    private final Map<String, Manifest> manifests = new HashMap<>();
    private final Map<String, String> blobs = new HashMap<>();

    FakeArtifactory manifest(String repository, String image, String reference, String mediaType, String fixture) {
        manifests.put(key(repository, image, reference),
                new Manifest(mediaType, reference, Fixtures.artifactory(fixture)));
        return this;
    }

    FakeArtifactory blob(String digest, String fixture) {
        blobs.put(digest, Fixtures.artifactory(fixture));
        return this;
    }

    @Override
    public List<String> dockerRepositories() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> images(String repository) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> tags(String repository, String image) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Manifest> manifest(String repository, String image, String reference) {
        return Optional.ofNullable(manifests.get(key(repository, image, reference)));
    }

    @Override
    public Optional<String> blob(String repository, String image, String digest) {
        return Optional.ofNullable(blobs.get(digest));
    }

    @Override
    public Optional<Instant> lastPushed(String repository, String image, String tag) {
        throw new UnsupportedOperationException();
    }

    private static String key(String repository, String image, String reference) {
        return repository + "/" + image + ":" + reference;
    }
}
