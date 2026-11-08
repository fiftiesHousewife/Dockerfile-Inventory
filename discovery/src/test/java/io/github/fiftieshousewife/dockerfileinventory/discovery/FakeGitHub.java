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
    final Map<String, Tree> listings = new LinkedHashMap<>();
    final Map<String, BlobText> blobs = new LinkedHashMap<>();
    final List<String> failingRepositories = new ArrayList<>();
    final List<String> treeCalls = new CopyOnWriteArrayList<>();
    final List<String> treeRequests = new CopyOnWriteArrayList<>();
    final List<List<BlobRequest>> blobCalls = new CopyOnWriteArrayList<>();
    final Map<ChangeRequest, LastChange> changes = new LinkedHashMap<>();
    final List<List<ChangeRequest>> changeCalls = new CopyOnWriteArrayList<>();
    final List<String> failingChanges = new ArrayList<>();

    FakeGitHub repository(String org, Repository repository) {
        repositoriesByOrg.computeIfAbsent(org, k -> new ArrayList<>()).add(repository);
        return this;
    }

    FakeGitHub tree(String repository, String commit, Tree tree) {
        trees.put(repository + "@" + commit, tree);
        return this;
    }

    FakeGitHub listing(String repository, String sha, Tree tree) {
        listings.put(repository + "@" + sha, tree);
        return this;
    }

    FakeGitHub blob(String sha, String text) {
        blobs.put(sha, new BlobText(sha, text, false, false));
        return this;
    }

    FakeGitHub change(ChangeRequest request, String changedIn, String changedAt) {
        changes.put(request, new LastChange(request.repository(), request.commitSha(), request.path(), changedIn,
                changedAt));
        return this;
    }

    static Repository repo(String nameWithOwner, String head) {
        return new Repository(nameWithOwner, "main", head, false, false, "2026-10-01T00:00:00Z");
    }

    static Tree tree(Tree.Entry... entries) {
        return new Tree(false, List.of(entries));
    }

    static Tree truncated(Tree.Entry... entries) {
        return new Tree(true, List.of(entries));
    }

    static Tree.Entry file(String path, String sha) {
        return new Tree.Entry(path, "100644", "blob", sha);
    }

    static Tree.Entry link(String path, String sha) {
        return new Tree.Entry(path, "120000", "blob", sha);
    }

    static Tree.Entry dir(String path, String sha) {
        return new Tree.Entry(path, "040000", "tree", sha);
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
    public Optional<Tree> tree(String repository, String sha, boolean recursive) {
        treeCalls.add(repository);
        treeRequests.add(repository + " " + sha + (recursive ? " recursive" : " listing"));
        if (failingRepositories.contains(repository)) {
            throw new GitHubException("GET tree for " + repository + " returned 502");
        }
        return Optional.ofNullable((recursive ? trees : listings).get(repository + "@" + sha));
    }

    @Override
    public List<BlobText> blobs(List<BlobRequest> requests) {
        blobCalls.add(List.copyOf(requests));
        return requests.stream().map(r -> blobs.get(r.sha())).filter(b -> b != null).toList();
    }

    @Override
    public List<LastChange> lastChanges(List<ChangeRequest> requests) {
        changeCalls.add(List.copyOf(requests));
        if (requests.stream().anyMatch(r -> failingChanges.contains(r.repository()))) {
            throw new GitHubException("POST graphql returned errors");
        }
        return requests.stream().map(r -> changes.getOrDefault(r, new LastChange(r.repository(), r.commitSha(),
                r.path(), null, null))).toList();
    }
}
