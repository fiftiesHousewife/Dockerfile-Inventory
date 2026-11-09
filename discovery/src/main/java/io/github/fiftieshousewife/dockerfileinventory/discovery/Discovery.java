package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class Discovery {

    record Summary(int repositories, int skipped, int scanned, int failed, int dockerfiles,
                   int uniqueBlobs, int fetched, int blobProblems, int lastChanges, int lastChangeProblems) {
    }

    private final GitHub gitHub;
    private final Scans scans;
    private final BlobFetch blobFetch;
    private final ChangeFetch changeFetch;
    private final DiscoveryOutput output;
    private final List<String> organizations;
    private final int concurrency;
    private final int tryRepositories;

    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency) {
        this(gitHub, releases, output, concurrency, List.of());
    }

    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency,
              List<String> organizations) {
        this(gitHub, releases, output, concurrency, organizations, 0);
    }

    // tryRepositories: stop once that many scanned repositories held a Dockerfile; 0 scans every one.
    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency,
              List<String> organizations, int tryRepositories) {
        this.gitHub = gitHub;
        this.concurrency = concurrency;
        this.tryRepositories = tryRepositories;
        this.scans = new Scans(new RepoScan(gitHub, releases), output, concurrency);
        this.blobFetch = new BlobFetch(gitHub, output);
        this.changeFetch = new ChangeFetch(gitHub, output);
        this.output = output;
        this.organizations = List.copyOf(organizations);
    }

    Summary run() {
        List<Repository> repositories = repositories();
        List<Repository> todo = unfinished(repositories, output.finishedRepositories());
        List<RepoScan.Result> results = tryRepositories > 0 ? scanUntilEnough(todo) : scans.scanAll(todo);
        int failed = (int) results.stream().filter(r -> !r.outcome().status().isFinal()).count();

        List<Located> manifest = output.manifest();
        Map<String, Located> byBlob = firstByBlob(manifest);
        Set<String> reported = output.reportedBlobs();
        BlobFetch.Fetched fetched = blobFetch.fetch(
                missing(byBlob.values(), sha -> output.hasBlob(sha) || reported.contains(sha)));
        ChangeFetch.Fetched lastChanges = changeFetch.fetch(unchanged(manifest, output.changes().stream()
                .map(c -> new ChangeRequest(c.repository(), c.commitSha(), c.path())).collect(Collectors.toSet())));

        return new Summary(repositories.size(), repositories.size() - todo.size(), results.size() - failed, failed,
                manifest.size(), byBlob.size(), fetched.stored(), fetched.problems(), lastChanges.recorded(),
                lastChanges.problems());
    }

    List<RepoScan.Result> scanUntilEnough(List<Repository> todo) {
        List<RepoScan.Result> results = new ArrayList<>();
        for (int from = 0; from < todo.size() && withDockerfiles(results) < tryRepositories; from += concurrency) {
            results.addAll(scans.scanAll(todo.subList(from, Math.min(from + concurrency, todo.size()))));
        }
        return results;
    }

    static long withDockerfiles(List<RepoScan.Result> results) {
        return results.stream().filter(result -> result.outcome().dockerfiles() > 0).count();
    }

    List<Repository> repositories() {
        List<String> scanned = organizations.isEmpty() ? gitHub.organizations() : organizations;
        return scanned.stream().flatMap(org -> gitHub.repositories(org).stream()).toList();
    }

    static List<Repository> unfinished(List<Repository> repositories, Set<String> finished) {
        return repositories.stream().filter(r -> !finished.contains(r.nameWithOwner())).toList();
    }

    static Map<String, Located> firstByBlob(List<Located> manifest) {
        return manifest.stream()
                .collect(Collectors.toMap(Located::blobSha, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    static List<ChangeRequest> unchanged(List<Located> manifest, Set<ChangeRequest> recorded) {
        return manifest.stream().map(l -> new ChangeRequest(l.repository(), l.commitSha(), l.path()))
                .filter(request -> !recorded.contains(request)).distinct().toList();
    }

    static List<BlobRequest> missing(Collection<Located> locations, Predicate<String> known) {
        return locations.stream()
                .filter(l -> !known.test(l.blobSha()))
                .map(l -> new BlobRequest(l.repository(), l.blobSha()))
                .toList();
    }
}
