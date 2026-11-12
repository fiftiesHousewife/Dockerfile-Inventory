package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.RateLimitWaitTooLong;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

final class Scans {

    private final RepoScan scan;
    private final DiscoveryOutput output;
    private final Semaphore permits;
    private final Progress progress;

    Scans(RepoScan scan, DiscoveryOutput output, int concurrency) {
        this(scan, output, concurrency, new Progress(() -> 0L, Instant::now));
    }

    Scans(RepoScan scan, DiscoveryOutput output, int concurrency, Progress progress) {
        this.scan = scan;
        this.progress = progress;
        this.output = output;
        this.permits = new Semaphore(concurrency);
    }

    List<RepoScan.Result> scanAll(List<Repository> repositories) {
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            return repositories.stream()
                    .map(r -> threads.submit(() -> scanOne(r)))
                    .toList()
                    .stream()
                    .map(Scans::result)
                    .toList();
        }
    }

    RepoScan.Result scanOne(Repository repository) throws InterruptedException {
        permits.acquire();
        try {
            RepoScan.Result result = scanOrFail(repository);
            output.record(result);
            progress.done(1, result.outcome().dockerfiles());
            return result;
        } finally {
            permits.release();
        }
    }

    RepoScan.Result scanOrFail(Repository repository) {
        try {
            return scan.scan(repository);
        } catch (GitHubException e) {
            return RepoScan.failed(repository, e);
        }
    }

    static RepoScan.Result result(Future<RepoScan.Result> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while scanning", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof SsoRequired || e.getCause() instanceof RateLimitWaitTooLong) {
                throw (RuntimeException) e.getCause();
            }
            throw new IllegalStateException("scan task failed", e.getCause());
        }
    }
}
