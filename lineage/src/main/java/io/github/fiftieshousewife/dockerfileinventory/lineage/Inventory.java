package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Records every tag in Artifactory with the config it names. Images run on virtual threads behind a semaphore, so
 * Artifactory sees at most {@code concurrency} images being read at once; each config is fetched once per digest,
 * and a rerun skips tags already recorded.
 */
public final class Inventory {

    private final Artifactory artifactory;
    private final LineageFiles files;
    private final int concurrency;
    private final Map<String, Optional<Resolved.Skipped>> configs = new ConcurrentHashMap<>();

    public Inventory(Artifactory artifactory, LineageFiles files, int concurrency) {
        this.artifactory = artifactory;
        this.files = files;
        this.concurrency = concurrency;
    }

    public void run() {
        Set<String> done = files.tags().stream()
                .map(t -> key(t.repository(), t.image(), t.tag()))
                .collect(Collectors.toSet());
        Semaphore permits = new Semaphore(concurrency);
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = artifactory.dockerRepositories().stream()
                    .flatMap(repository -> artifactory.images(repository).stream()
                            .map(image -> threads.submit(() -> {
                                permits.acquire();
                                try {
                                    image(repository, image, done);
                                } finally {
                                    permits.release();
                                }
                                return null;
                            })))
                    .toList();
            for (var future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while reading Artifactory", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("reading an image failed", e.getCause());
        }
    }

    private void image(String repository, String image, Set<String> done) {
        for (String tag : artifactory.tags(repository, image)) {
            if (done.contains(key(repository, image, tag))) {
                continue;
            }
            var pushed = artifactory.lastPushed(repository, image, tag).orElse(null);
            TagRow row = switch (ImageNodes.locate(artifactory, repository, image, tag)) {
                case Location.Found found -> config(repository, image, found.configDigest())
                        .map(s -> new TagRow(repository, image, tag, pushed, null, s.reason(), s.detail()))
                        .orElseGet(() -> new TagRow(repository, image, tag, pushed, found.configDigest(), null, null));
                case Resolved.Skipped s -> new TagRow(repository, image, tag, pushed, null, s.reason(), s.detail());
            };
            files.record(row);
        }
    }

    /** Stores the config unless it is stored already; empty when it is, or why it could not be. Once per digest. */
    private Optional<Resolved.Skipped> config(String repository, String image, String digest) {
        return configs.computeIfAbsent(digest, d -> {
            if (files.hasConfig(d)) {
                return Optional.empty();
            }
            Optional<String> text = artifactory.blob(repository, image, d);
            if (text.isEmpty()) {
                return Optional.of(new Resolved.Skipped(Resolved.Reason.GONE, d));
            }
            if (ImageConfig.parse(text.get()).isEmpty()) {
                return Optional.of(new Resolved.Skipped(Resolved.Reason.UNREADABLE_CONFIG, d));
            }
            files.storeConfig(d, text.get());
            return Optional.empty();
        });
    }

    private static String key(String repository, String image, String tag) {
        return repository + "/" + image + ":" + tag;
    }
}
