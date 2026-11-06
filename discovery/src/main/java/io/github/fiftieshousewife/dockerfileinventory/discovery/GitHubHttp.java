package io.github.fiftieshousewife.dockerfileinventory.discovery;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * GitHub Enterprise Server over HTTP: REST for organisations and trees, GraphQL for repositories and blob text.
 * {@code apiBase} is the server's {@code /api} URL; REST lives under {@code /api/v3}, GraphQL at {@code /api/graphql}.
 * Only {@code java.net.http} is used, so the scanner image needs no git binary.
 */
public final class GitHubHttp implements GitHub {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_ATTEMPTS = 6;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final String REPOSITORIES_QUERY = """
            query($org: String!, $cursor: String) {
              organization(login: $org) {
                repositories(first: 100, after: $cursor) {
                  pageInfo { hasNextPage endCursor }
                  nodes {
                    nameWithOwner isArchived isFork pushedAt
                    defaultBranchRef { name target { oid } }
                  }
                }
              }
            }""";

    private final HttpClient http;
    private final URI apiBase;
    private final String token;
    private final String restPrefix;
    private final ObjectMapper json = new ObjectMapper();

    public GitHubHttp(URI apiBase, String token) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), apiBase, token, restPrefix(apiBase));
    }

    GitHubHttp(HttpClient http, URI apiBase, String token) {
        this(http, apiBase, token, restPrefix(apiBase));
    }

    GitHubHttp(HttpClient http, URI apiBase, String token, String restPrefix) {
        this.http = http;
        this.apiBase = URI.create(apiBase.toString().replaceAll("/+$", "") + "/");
        this.token = token;
        this.restPrefix = restPrefix;
    }

    /**
     * GitHub Enterprise Server serves REST under {@code /api/v3/} and GraphQL at {@code /api/graphql}; github.com
     * (and Enterprise Cloud) serve REST at the root of {@code api.github.com} and GraphQL at {@code /graphql}.
     */
    static String restPrefix(URI apiBase) {
        return "api.github.com".equals(apiBase.getHost()) ? "" : "v3/";
    }

    @Override
    public List<String> organizations() {
        List<String> logins = new ArrayList<>();
        long since = 0;
        while (true) {
            JsonNode page = rest(restPrefix + "organizations?per_page=%d&since=%d".formatted(PAGE_SIZE, since)).orElseThrow();
            if (page.isEmpty()) {
                return logins;
            }
            page.forEach(org -> logins.add(org.path("login").asString()));
            since = page.get(page.size() - 1).path("id").asLong();
        }
    }

    @Override
    public List<Repository> repositories(String organization) {
        List<Repository> repositories = new ArrayList<>();
        String cursor = null;
        do {
            ObjectNode variables = json.createObjectNode().put("org", organization).put("cursor", cursor);
            JsonNode page = graphql(REPOSITORIES_QUERY, variables).path("organization").path("repositories");
            page.path("nodes").forEach(node -> repositories.add(repository(node)));
            cursor = page.path("pageInfo").path("hasNextPage").asBoolean() ? page.path("pageInfo").path("endCursor").asString() : null;
        } while (cursor != null);
        return repositories;
    }

    @Override
    public Optional<Tree> tree(String repository, String sha, boolean recursive) {
        return rest(restPrefix + "repos/%s/git/trees/%s%s".formatted(repository, sha, recursive ? "?recursive=1" : ""))
                .map(GitHubHttp::tree);
    }

    @Override
    public List<BlobText> blobs(List<BlobRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        JsonNode data = graphql(blobQuery(requests), json.createObjectNode());
        List<BlobText> blobs = new ArrayList<>();
        for (BlobRequest request : requests) {
            JsonNode object = data.path(alias(request)).path("object");
            if (object.isObject()) {
                blobs.add(new BlobText(request.sha(), object.path("text").stringValue(null),
                        object.path("isTruncated").asBoolean(), object.path("isBinary").asBoolean()));
            }
        }
        return blobs;
    }

    static String blobQuery(List<BlobRequest> requests) {
        return requests.stream()
                .distinct()
                .map(r -> "  %s: repository(owner: %s, name: %s) { object(oid: \"%s\") { ... on Blob { text isTruncated isBinary } } }"
                        .formatted(alias(r), quoted(owner(r.repository())), quoted(name(r.repository())), r.sha()))
                .collect(Collectors.joining("\n", "query {\n", "\n}"));
    }

    private static String alias(BlobRequest request) {
        return "b" + request.sha();
    }

    private static String owner(String repository) {
        return repository.substring(0, repository.indexOf('/'));
    }

    private static String name(String repository) {
        return repository.substring(repository.indexOf('/') + 1);
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Repository repository(JsonNode node) {
        JsonNode branch = node.path("defaultBranchRef");
        return new Repository(
                node.path("nameWithOwner").asString(),
                branch.path("name").stringValue(null),
                branch.path("target").path("oid").stringValue(null),
                node.path("isArchived").asBoolean(),
                node.path("isFork").asBoolean(),
                node.path("pushedAt").stringValue(null));
    }

    private static Tree tree(JsonNode node) {
        List<Tree.Entry> entries = new ArrayList<>();
        node.path("tree").forEach(e -> entries.add(
                new Tree.Entry(e.path("path").asString(), e.path("type").asString(), e.path("sha").asString())));
        return new Tree(node.path("truncated").asBoolean(), entries);
    }

    /** A REST GET; empty for 404, 409 (empty repository) and 422 (not a commit), which mean "no tree here". */
    private Optional<JsonNode> rest(String path) {
        HttpResponse<String> response = send(request(path).GET().build());
        return switch (response.statusCode()) {
            case 200 -> Optional.of(parse(response));
            case 404, 409, 422 -> Optional.empty();
            default -> throw failure(response);
        };
    }

    private JsonNode graphql(String query, ObjectNode variables) {
        String body = json.createObjectNode().put("query", query).set("variables", variables).toString();
        HttpResponse<String> response = send(request("graphql")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        if (response.statusCode() != 200) {
            throw failure(response);
        }
        JsonNode result = parse(response);
        if (result.has("errors") && !result.path("errors").isEmpty()) {
            throw new GitHubException("GraphQL errors: " + result.path("errors"));
        }
        return result.path("data");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(apiBase.resolve(path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            for (int attempt = 1; ; attempt++) {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Optional<Duration> wait = RateLimit.waitBeforeRetry(response.statusCode(), response.headers(), Instant.now());
                if (wait.isEmpty() || attempt == MAX_ATTEMPTS) {
                    return response;
                }
                Thread.sleep(wait.get());
            }
        } catch (IOException e) {
            throw new GitHubException(request.method() + " " + request.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("interrupted calling " + request.uri(), e);
        }
    }

    private JsonNode parse(HttpResponse<String> response) {
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            throw new GitHubException("unreadable JSON from " + response.uri(), e);
        }
    }

    private static GitHubException failure(HttpResponse<String> response) {
        String body = response.body();
        return new GitHubException("%s %s returned %d: %s".formatted(response.request().method(), response.uri(),
                response.statusCode(), body.length() > 300 ? body.substring(0, 300) : body));
    }
}
