package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class GitHubRequests {

    private static final int MAX_BODY_IN_ERROR = 300;

    private final GitHubSender sender;
    private final URI apiBase;
    private final Credential token;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    GitHubRequests(HttpClient http, URI apiBase, Credential token, RetryPolicy retries, Duration timeout,
            Sleeper sleeper) {
        this(new GitHubSender(http, token, retries, sleeper, Throttle.NONE), apiBase, token, timeout);
    }

    GitHubRequests(GitHubSender sender, URI apiBase, Credential token, Duration timeout) {
        this.sender = sender;
        this.apiBase = withTrailingSlash(apiBase);
        this.token = token;
        this.timeout = timeout;
    }

    static URI withTrailingSlash(URI uri) {
        return URI.create(uri.toString().replaceAll("/+$", "") + "/");
    }

    ObjectNode object() {
        return json.createObjectNode();
    }

    // 404, 409 (an empty repository) and 422 (not a commit) all mean "no tree here".
    Optional<JsonNode> rest(String path) {
        HttpResponse<String> response = sender.send(() -> request(path).GET().build());
        return switch (response.statusCode()) {
            case 200 -> Optional.of(parse(response));
            case 404, 409, 422 -> Optional.empty();
            default -> throw failure(response);
        };
    }

    JsonNode post(String path) {
        HttpResponse<String> response = sender.send(() -> request(path)
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw failure(response);
        }
        return parse(response);
    }

    // GraphQL reports errors in a 200 response, beside whatever data it could resolve.
    JsonNode graphql(String query, ObjectNode variables) {
        HttpResponse<String> response = postGraphql(object().put("query", query).set("variables", variables));
        if (response.statusCode() != 200) {
            throw failure(response);
        }
        JsonNode result = parse(response);
        if (hasErrors(result)) {
            throw new GitHubException("GraphQL errors: " + result.path("errors"));
        }
        return result.path("data");
    }

    // Some tokens may not use GraphQL (a fine-grained token on a server that does not take them there): GitHub
    // answers 401 or 403, or 200 with errors, to the smallest query any other token may ask.
    boolean graphqlAllowed() {
        HttpResponse<String> response = postGraphql(object().put("query", "{ rateLimit { remaining } }"));
        return switch (response.statusCode()) {
            case 200 -> !hasErrors(parse(response));
            case 401, 403 -> false;
            default -> throw failure(response);
        };
    }

    private HttpResponse<String> postGraphql(ObjectNode body) {
        return sender.send(() -> request("graphql").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
    }

    static boolean hasErrors(JsonNode result) {
        return result.has("errors") && !result.path("errors").isEmpty();
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
                .timeout(timeout)
                .header("Authorization", "Bearer " + token.token())
                .header("Accept", "application/vnd.github+json");
    }
}
