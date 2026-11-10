package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class GitHubHttp implements GitHub {

    private static final int PAGE_SIZE = 100;

    private final GitHubRequests requests;
    private final String restPrefix;

    GitHubHttp(GitHubApi api, HttpSettings settings, Credential token, Throttle throttle) {
        this(new GitHubRequests(new GitHubSender(settings.client(), token, settings.retries(), Sleeper.REAL, throttle),
                api.base(), token, settings.requestTimeout()), api.restPrefix());
    }

    GitHubHttp(GitHubRequests requests, String restPrefix) {
        this.requests = requests;
        this.restPrefix = restPrefix;
    }

    @Override
    public List<String> organizations() {
        List<String> logins = new ArrayList<>();
        long since = 0;
        while (true) {
            JsonNode page = requests.rest(organizationsPath(since)).orElseThrow();
            if (page.isEmpty()) {
                return logins;
            }
            logins.addAll(logins(page));
            since = page.get(page.size() - 1).path("id").asLong();
        }
    }

    String organizationsPath(long since) {
        return restPrefix + "organizations?per_page=%d&since=%d".formatted(PAGE_SIZE, since);
    }

    static List<String> logins(JsonNode page) {
        return StreamSupport.stream(page.spliterator(), false).map(org -> org.path("login").asString()).toList();
    }

    @Override
    public List<Repository> repositories(String organization) {
        List<Repository> repositories = new ArrayList<>();
        Optional<String> cursor = Optional.empty();
        do {
            ObjectNode variables = requests.object().put("org", organization).put("cursor", cursor.orElse(null));
            JsonNode page = requests.graphql(GraphQlRepositories.QUERY, variables)
                    .path("organization").path("repositories");
            page.path("nodes").forEach(node -> repositories.add(GraphQlRepositories.repository(node)));
            cursor = GraphQlRepositories.nextCursor(page);
        } while (cursor.isPresent());
        return repositories;
    }

    @Override
    public Optional<Tree> tree(String repository, String sha, boolean recursive) {
        return requests.rest(RestTrees.path(restPrefix, repository, sha, recursive)).map(RestTrees::tree);
    }

    @Override
    public List<BlobText> blobs(List<BlobRequest> blobs) {
        if (blobs.isEmpty()) {
            return List.of();
        }
        return GraphQlBlobs.read(requests.graphql(GraphQlBlobs.query(blobs), requests.object()), blobs);
    }

    @Override
    public List<LastChange> lastChanges(List<ChangeRequest> changes) {
        if (changes.isEmpty()) {
            return List.of();
        }
        return GraphQlHistory.read(requests.graphql(GraphQlHistory.query(changes), requests.object()), changes);
    }
}
