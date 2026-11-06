package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Repositories, images, tags, manifests and blobs held in memory, keyed as Artifactory addresses them. */
final class FakeArtifactory implements Artifactory {

    private final Map<String, Manifest> manifests = new ConcurrentHashMap<>();
    private final Map<String, String> blobs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> images = new ConcurrentHashMap<>();
    private final Map<String, List<String>> tags = new ConcurrentHashMap<>();
    private final Map<String, Instant> pushed = new ConcurrentHashMap<>();
    final AtomicInteger manifestCalls = new AtomicInteger();
    final Map<String, AtomicInteger> blobCalls = new ConcurrentHashMap<>();

    FakeArtifactory manifest(String repository, String image, String reference, String mediaType, String fixture) {
        manifests.put(key(repository, image, reference),
                new Manifest(mediaType, reference, Fixtures.artifactory(fixture)));
        return this;
    }

    FakeArtifactory blob(String digest, String fixture) {
        blobs.put(digest, Fixtures.artifactory(fixture));
        return this;
    }

    /** A tag listed in the catalogue, pushed at {@code when}, whose manifest is the fixture. */
    FakeArtifactory tag(String repository, String image, String tag, String when, String mediaType, String fixture) {
        images.computeIfAbsent(repository, r -> new ArrayList<>());
        if (!images.get(repository).contains(image)) {
            images.get(repository).add(image);
        }
        tags.computeIfAbsent(repository + "/" + image, k -> new ArrayList<>()).add(tag);
        pushed.put(key(repository, image, tag), Instant.parse(when));
        return manifest(repository, image, tag, mediaType, fixture);
    }

    @Override
    public List<String> dockerRepositories() {
        return images.keySet().stream().sorted().toList();
    }

    @Override
    public List<String> images(String repository) {
        return images.getOrDefault(repository, List.of());
    }

    @Override
    public List<String> tags(String repository, String image) {
        return tags.getOrDefault(repository + "/" + image, List.of());
    }

    @Override
    public Optional<Manifest> manifest(String repository, String image, String reference) {
        manifestCalls.incrementAndGet();
        return Optional.ofNullable(manifests.get(key(repository, image, reference)));
    }

    @Override
    public Optional<String> blob(String repository, String image, String digest) {
        blobCalls.computeIfAbsent(digest, d -> new AtomicInteger()).incrementAndGet();
        return Optional.ofNullable(blobs.get(digest));
    }

    @Override
    public Optional<Instant> lastPushed(String repository, String image, String tag) {
        return Optional.ofNullable(pushed.get(key(repository, image, tag)));
    }

    private static String key(String repository, String image, String reference) {
        return repository + "/" + image + ":" + reference;
    }
}
