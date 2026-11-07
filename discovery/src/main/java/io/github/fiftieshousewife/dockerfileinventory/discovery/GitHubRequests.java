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
import java.util.Optional;

final class GitHubRequests {

    static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 6;
    private static final int MAX_BODY_IN_ERROR = 300;

    private final HttpClient http;
    private final URI apiBase;
    private final String token;
    private final ObjectMapper json = new ObjectMapper();

    GitHubRequests(HttpClient http, URI apiBase, String token) {
        this.http = http;
        this.apiBase = withTrailingSlash(apiBase);
        this.token = token;
    }

    static URI withTrailingSlash(URI uri) {
        return URI.create(uri.toString().replaceAll("/+$", "") + "/");
    }

    ObjectNode object() {
        return json.createObjectNode();
    }

    // 404, 409 (an empty repository) and 422 (not a commit) all mean "no tree here".
    Optional<JsonNode> rest(String path) {
        HttpResponse<String> response = send(request(path).GET().build());
        return switch (response.statusCode()) {
            case 200 -> Optional.of(parse(response));
            case 404, 409, 422 -> Optional.empty();
            default -> throw failure(response);
        };
    }

    // GraphQL reports errors in a 200 response, beside whatever data it could resolve.
    JsonNode graphql(String query, ObjectNode variables) {
        String body = object().put("query", query).set("variables", variables).toString();
        HttpResponse<String> response = send(request("graphql")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        if (response.statusCode() != 200) {
            throw failure(response);
        }
        JsonNode result = parse(response);
        if (hasErrors(result)) {
            throw new GitHubException("GraphQL errors: " + result.path("errors"));
        }
        return result.path("data");
    }

    static boolean hasErrors(JsonNode result) {
        return result.has("errors") && !result.path("errors").isEmpty();
    }

    HttpResponse<String> send(HttpRequest request) {
        try {
            for (int attempt = 1; ; attempt++) {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Optional<Duration> wait =
                        RateLimit.waitBeforeRetry(response.statusCode(), response.headers(), Instant.now());
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

    JsonNode parse(HttpResponse<String> response) {
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            throw new GitHubException("unreadable JSON from " + response.uri(), e);
        }
    }

    static GitHubException failure(HttpResponse<String> response) {
        String body = response.body();
        String shown = body.length() > MAX_BODY_IN_ERROR ? body.substring(0, MAX_BODY_IN_ERROR) : body;
        return new GitHubException("%s %s returned %d: %s".formatted(response.request().method(), response.uri(),
                response.statusCode(), shown));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(apiBase.resolve(path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json");
    }
}
