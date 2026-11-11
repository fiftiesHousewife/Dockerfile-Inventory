package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

// GitHub over REST alone, for a token GraphQL refuses: one call a blob and a location where GraphQL takes fifty.
final class GitHubRest implements GitHub {

    private final GitHubRequests requests;
    private final String restPrefix;
    private final GitHubHttp shared;
    private final boolean lastChanges;

    GitHubRest(GitHubApi api, HttpSettings settings, Credential token, Throttle throttle, boolean lastChanges) {
        this(new GitHubRequests(new GitHubSender(settings.client(), token, settings.retries(), Sleeper.REAL, throttle),
                api.base(), token, settings.requestTimeout()), api.restPrefix(), lastChanges);
    }

    GitHubRest(GitHubRequests requests, String restPrefix, boolean lastChanges) {
        this.requests = requests;
        this.restPrefix = restPrefix;
        this.shared = new GitHubHttp(requests, restPrefix);
        this.lastChanges = lastChanges;
    }

    @Override
    public int calls(Pending pending) {
        return pending.blobs().size() + pending.yamls().size() + (lastChanges ? pending.changes().size() : 0);
    }

    @Override
    public List<String> organizations() {
        return shared.organizations();
    }

    @Override
    public List<Repository> repositories(String organization) {
        List<Repository> repositories = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode listed = requests.rest(RestRepositories.path(restPrefix, organization, page)).orElseThrow();
            if (listed.isEmpty()) {
                return repositories;
            }
            listed.forEach(node -> repositories.add(RestRepositories.repository(node)));
        }
    }

    @Override
    public String headCommit(Repository repository) {
        if (repository.headCommit() != null || repository.defaultBranch() == null) {
            return repository.headCommit();
        }
        return requests.rest(RestRepositories.branchPath(restPrefix, repository.nameWithOwner(),
                repository.defaultBranch())).map(RestRepositories::headCommit).orElse(null);
    }

    @Override
    public Optional<Tree> tree(String repository, String sha, boolean recursive) {
        return shared.tree(repository, sha, recursive);
    }

    @Override
    public List<BlobText> blobs(List<BlobRequest> blobs) {
        return blobs.stream().distinct().flatMap(request -> requests.rest(RestBlobs.path(restPrefix, request))
                .map(node -> RestBlobs.blob(request.sha(), node)).stream()).toList();
    }

    @Override
    public List<LastChange> lastChanges(List<ChangeRequest> changes) {
        if (!lastChanges) {
            return List.of();
        }
        return changes.stream().distinct().map(request -> RestHistory.lastChange(request,
                requests.rest(RestHistory.path(restPrefix, request)).orElse(MissingNode.getInstance()))).toList();
    }
}
