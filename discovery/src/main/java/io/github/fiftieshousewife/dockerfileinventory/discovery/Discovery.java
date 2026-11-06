package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 0 end to end: enumerate repositories, scan each one's tree, then fetch every Dockerfile blob not
 * already stored, about 50 per GraphQL call. Scans run on virtual threads behind a semaphore so the server
 * sees at most {@code concurrency} calls at once. Rerunning against the same output resumes.
 */
public final class Discovery {

    public record Summary(int repositories, int skipped, int scanned, int failed, int dockerfiles,
                          int uniqueBlobs, int fetched, int blobProblems) {
    }

    static final int BLOB_BATCH = 50;

    private final GitHub gitHub;
    private final RepoScan scan;
    private final DiscoveryOutput output;
    private final int concurrency;
    private final List<String> organizations;

    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency) {
        this(gitHub, releases, output, concurrency, List.of());
    }

    /** {@code organizations}, when not empty, are the only ones scanned; otherwise every organisation is. */
    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency,
              List<String> organizations) {
        this.gitHub = gitHub;
        this.scan = new RepoScan(gitHub, releases);
        this.output = output;
        this.concurrency = concurrency;
        this.organizations = List.copyOf(organizations);
    }

    public Summary run() {
        List<String> scanned = organizations.isEmpty() ? gitHub.organizations() : organizations;
        List<Repository> repositories = scanned.stream()
                .flatMap(org -> gitHub.repositories(org).stream())
                .toList();
        Set<String> finished = output.finishedRepositories();
        List<Repository> todo = repositories.stream().filter(r -> !finished.contains(r.nameWithOwner())).toList();
        List<RepoScan.Result> results = scanAll(todo);
        int failed = (int) results.stream().filter(r -> !r.outcome().status().isFinal()).count();

        List<Located> manifest = output.manifest();
        Map<String, Located> byBlob = manifest.stream()
                .collect(Collectors.toMap(Located::blobSha, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<String> reported = output.reportedBlobs();
        List<BlobRequest> missing = byBlob.values().stream()
                .filter(l -> !output.hasBlob(l.blobSha()) && !reported.contains(l.blobSha()))
                .map(l -> new BlobRequest(l.repository(), l.blobSha()))
                .toList();
        int[] fetched = fetchBlobs(missing);

        return new Summary(repositories.size(), repositories.size() - todo.size(), results.size() - failed, failed,
                manifest.size(), byBlob.size(), fetched[0], fetched[1]);
    }

    private List<RepoScan.Result> scanAll(List<Repository> repositories) {
        Semaphore permits = new Semaphore(concurrency);
        List<RepoScan.Result> results = new ArrayList<>();
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = repositories.stream().map(r -> threads.submit(() -> scanOne(r, permits))).toList();
            for (var future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while scanning", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("scan task failed", e.getCause());
        }
        return results;
    }

    private RepoScan.Result scanOne(Repository repository, Semaphore permits) throws InterruptedException {
        permits.acquire();
        try {
            RepoScan.Result result;
            try {
                result = scan.scan(repository);
            } catch (GitHubException e) {
                result = RepoScan.failed(repository, e);
            }
            output.record(result);
            return result;
        } finally {
            permits.release();
        }
    }

    /** Returns {fetched, problems}. */
    private int[] fetchBlobs(List<BlobRequest> requests) {
        int fetched = 0;
        int problems = 0;
        for (int from = 0; from < requests.size(); from += BLOB_BATCH) {
            List<BlobRequest> batch = requests.subList(from, Math.min(from + BLOB_BATCH, requests.size()));
            Map<String, BlobText> texts = gitHub.blobs(batch).stream()
                    .collect(Collectors.toMap(BlobText::sha, Function.identity(), (a, b) -> a));
            for (BlobRequest request : batch) {
                BlobText blob = texts.get(request.sha());
                String problem = problem(blob);
                if (problem == null) {
                    output.storeBlob(request.sha(), blob.text());
                    fetched++;
                } else {
                    output.recordBlobFinding(new DiscoveryOutput.BlobFinding(request.sha(), request.repository(), problem));
                    problems++;
                }
            }
        }
        return new int[] {fetched, problems};
    }

    static String problem(BlobText blob) {
        if (blob == null) {
            return "not found";
        }
        if (blob.binary()) {
            return "binary";
        }
        if (blob.truncated() || blob.text() == null) {
            return "truncated";
        }
        return null;
    }
}
