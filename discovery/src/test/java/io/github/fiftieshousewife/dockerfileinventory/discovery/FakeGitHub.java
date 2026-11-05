package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

final class FakeGitHub implements GitHub {

    final Map<String, List<Repository>> repositoriesByOrg = new LinkedHashMap<>();
    final Map<String, Tree> trees = new LinkedHashMap<>();
    final Map<String, BlobText> blobs = new LinkedHashMap<>();
    final List<String> failingRepositories = new ArrayList<>();
    final List<String> treeCalls = new CopyOnWriteArrayList<>();
    final List<List<BlobRequest>> blobCalls = new CopyOnWriteArrayList<>();

    FakeGitHub repository(String org, Repository repository) {
        repositoriesByOrg.computeIfAbsent(org, k -> new ArrayList<>()).add(repository);
        return this;
    }

    FakeGitHub tree(String repository, String commit, Tree tree) {
        trees.put(repository + "@" + commit, tree);
        return this;
    }

    FakeGitHub blob(String sha, String text) {
        blobs.put(sha, new BlobText(sha, text, false, false));
        return this;
    }

    static Repository repo(String nameWithOwner, String head) {
        return new Repository(nameWithOwner, "main", head, false, false, "2026-10-01T00:00:00Z");
    }

    static Tree tree(Tree.Entry... entries) {
        return new Tree(false, List.of(entries));
    }

    static Tree.Entry file(String path, String sha) {
        return new Tree.Entry(path, "blob", sha);
    }

    @Override
    public List<String> organizations() {
        return List.copyOf(repositoriesByOrg.keySet());
    }

    @Override
    public List<Repository> repositories(String organization) {
        return repositoriesByOrg.getOrDefault(organization, List.of());
    }

    @Override
    public Optional<Tree> tree(String repository, String commitSha) {
        treeCalls.add(repository);
        if (failingRepositories.contains(repository)) {
            throw new GitHubException("GET tree for " + repository + " returned 502");
        }
        return Optional.ofNullable(trees.get(repository + "@" + commitSha));
    }

    @Override
    public List<BlobText> blobs(List<BlobRequest> requests) {
        blobCalls.add(List.copyOf(requests));
        return requests.stream().map(r -> blobs.get(r.sha())).filter(b -> b != null).toList();
    }
}
