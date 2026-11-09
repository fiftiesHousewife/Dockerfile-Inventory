package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

final class Inventory {

    private final Artifactory artifactory;
    private final LineageFiles files;
    private final int concurrency;
    private final Map<String, Optional<Resolved.Skipped>> configs = new ConcurrentHashMap<>();

    Inventory(Artifactory artifactory, LineageFiles files, int concurrency) {
        this.artifactory = artifactory;
        this.files = files;
        this.concurrency = concurrency;
    }

    void run() {
        Set<String> done = files.tags().stream()
                .map(t -> key(t.repository(), t.image(), t.tag()))
                .collect(Collectors.toSet());
        Semaphore permits = new Semaphore(concurrency);
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            awaitAll(artifactory.dockerRepositories().stream()
                    .flatMap(repository -> readable(repository).stream()
                            .map(image -> threads.submit(() -> limited(permits, repository, image, done))))
                    .toList());
        }
    }

    Void limited(Semaphore permits, String repository, String image, Set<String> done) throws InterruptedException {
        permits.acquire();
        try {
            image(repository, image, done);
        } finally {
            permits.release();
        }
        return null;
    }

    static void awaitAll(List<Future<Void>> futures) {
        futures.forEach(Inventory::await);
    }

    static void await(Future<Void> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while reading Artifactory", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("reading an image failed", e.getCause());
        }
    }

    // A repository or image the token may not read is recorded as refused; the rest of the run goes on.
    List<String> readable(String repository) {
        try {
            return artifactory.images(repository);
        } catch (AccessRefused e) {
            files.record(refused(repository, "*", e));
            return List.of();
        }
    }

    void image(String repository, String image, Set<String> done) {
        List<String> tags;
        try {
            tags = artifactory.tags(repository, image);
        } catch (AccessRefused e) {
            files.record(refused(repository, image, e));
            return;
        }
        tags.stream()
                .filter(tag -> !done.contains(key(repository, image, tag)))
                .forEach(tag -> files.record(row(repository, image, tag)));
    }

    static TagRow refused(String repository, String image, AccessRefused e) {
        return new TagRow(repository, image, "*", null, null, Resolved.Reason.REFUSED, e.getMessage());
    }

    TagRow row(String repository, String image, String tag) {
        Instant pushed = artifactory.lastPushed(repository, image, tag).orElse(null);
        return switch (ImageNodes.locate(artifactory, repository, image, tag)) {
            case Location.Found found -> config(repository, image, found.configDigest())
                    .map(s -> new TagRow(repository, image, tag, pushed, null, s.reason(), s.detail()))
                    .orElseGet(() -> found(repository, image, tag, pushed, found));
            case Resolved.Skipped s -> new TagRow(repository, image, tag, pushed, null, s.reason(), s.detail());
        };
    }

    TagRow found(String repository, String image, String tag, Instant pushed, Location.Found found) {
        Optional<DownloadStats> stats = artifactory.stats(repository, image, tag);
        return new TagRow(repository, image, tag, pushed, found.configDigest(), null, null, found.size(),
                found.architectures(), found.manifestDigest(), stats.map(DownloadStats::downloadCount).orElse(null),
                stats.map(DownloadStats::lastDownloaded).orElse(null));
    }

    Optional<Resolved.Skipped> config(String repository, String image, String digest) {
        return configs.computeIfAbsent(digest,
                d -> files.hasConfig(d) ? Optional.empty() : fetch(repository, image, d));
    }

    Optional<Resolved.Skipped> fetch(String repository, String image, String digest) {
        Optional<String> text = artifactory.blob(repository, image, digest);
        if (text.isEmpty()) {
            return Optional.of(new Resolved.Skipped(Resolved.Reason.GONE, digest));
        }
        if (ImageConfig.parse(text.get()).isEmpty()) {
            return Optional.of(new Resolved.Skipped(Resolved.Reason.UNREADABLE_CONFIG, digest));
        }
        files.storeConfig(digest, text.get());
        return Optional.empty();
    }

    private static String key(String repository, String image, String tag) {
        return repository + "/" + image + ":" + tag;
    }
}
