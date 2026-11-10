package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Discovery {

    record Summary(int repositories, int skipped, int scanned, int failed, int dockerfiles,
                   int uniqueBlobs, int fetched, int blobProblems, int lastChanges, int lastChangeProblems,
                   int yamlLocations, int yamlFetched) {
    }

    private final GitHub gitHub;
    private final Scans scans;
    private final BlobFetch blobFetch;
    private final BlobFetch yamlFetch;
    private final ChangeFetch changeFetch;
    private final DiscoveryOutput output;
    private final List<String> organizations;
    private final int concurrency;
    private final int tryRepositories;
    private final Selection selection;
    private final int maxDockerfiles;
    private final Confirmation confirmation;

    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency) {
        this(gitHub, releases, output, concurrency, List.of());
    }

    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency,
              List<String> organizations) {
        this(gitHub, releases, output, concurrency, organizations, 0);
    }

    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency,
              List<String> organizations, int tryRepositories) {
        this(gitHub, releases, output, concurrency, organizations, tryRepositories, Selection.ALL, 0,
                Confirmation.ALWAYS);
    }

    Discovery(GitHub gitHub, ReleaseIndex releases, DiscoveryOutput output, int concurrency,
              List<String> organizations, int tryRepositories, Selection selection, int maxDockerfiles,
              Confirmation confirmation) {
        this.gitHub = gitHub;
        this.selection = selection;
        this.maxDockerfiles = maxDockerfiles;
        this.confirmation = confirmation;
        this.concurrency = concurrency;
        this.tryRepositories = tryRepositories;
        this.scans = new Scans(new RepoScan(gitHub, releases), output, concurrency);
        this.blobFetch = new BlobFetch(gitHub, output);
        this.yamlFetch = new BlobFetch(gitHub, output, output::storeYaml);
        this.changeFetch = new ChangeFetch(gitHub, output);
        this.output = output;
        this.organizations = List.copyOf(organizations);
    }

    Summary run() {
        List<Repository> repositories = selection.of(repositories());
        List<Repository> todo = unfinished(repositories, output.finishedRepositories());
        List<RepoScan.Result> results = tryRepositories > 0 ? scanUntilEnough(todo) : scans.scanAll(todo);
        int failed = failed(results);
        List<Located> manifest = output.manifest();
        Map<String, Located> byBlob = firstByBlob(manifest);
        List<Located> yaml = output.yaml();
        Pending pending = Pending.of(output, manifest, byBlob.values(), yaml);
        goOn(firstPass(repositories.size(), manifest, byBlob.size(), pending.calls()));

        BlobFetch.Fetched fetched = blobFetch.fetch(pending.blobs());
        BlobFetch.Fetched fetchedYaml = yamlFetch.fetch(pending.yamls());
        ChangeFetch.Fetched lastChanges = changeFetch.fetch(pending.changes());
        return new Summary(repositories.size(), repositories.size() - todo.size(), results.size() - failed, failed,
                manifest.size(), byBlob.size(), fetched.stored(), fetched.problems(), lastChanges.recorded(),
                lastChanges.problems(), yaml.size(), fetchedYaml.stored());
    }

    static int failed(List<RepoScan.Result> results) {
        return (int) results.stream().filter(r -> !r.outcome().status().isFinal()).count();
    }

    FirstPass firstPass(int repositories, List<Located> manifest, int distinct, int callsLeft) {
        return new FirstPass(repositories, (int) manifest.stream().map(Located::repository).distinct().count(),
                manifest.size(), distinct, callsLeft, output.dir().toString());
    }

    void goOn(FirstPass pass) {
        if (maxDockerfiles > 0 && pass.distinct() > maxDockerfiles) {
            throw new RunStopped("found " + pass.distinct() + " distinct Dockerfiles, more than MAX_DOCKERFILES "
                    + maxDockerfiles + ": narrow the selection or raise it");
        }
        if (!confirmation.proceed(pass)) {
            throw new RunStopped("stopped before fetching: the first pass is kept in the output folder, and a rerun "
                    + "starts from it");
        }
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
}
