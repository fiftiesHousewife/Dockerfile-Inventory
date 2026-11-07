package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class GitHubRequests {

    private static final int MAX_BODY_IN_ERROR = 300;

    interface Sleeper {
        void sleep(Duration wait) throws InterruptedException;
    }

    private final HttpClient http;
    private final URI apiBase;
    private final TokenSource token;
    private final RetryPolicy retries;
    private final Duration timeout;
    private final Sleeper sleeper;
    private final ObjectMapper json = new ObjectMapper();

    GitHubRequests(HttpClient http, URI apiBase, TokenSource token, RetryPolicy retries, Duration timeout,
            Sleeper sleeper) {
        this.http = http;
        this.apiBase = withTrailingSlash(apiBase);
        this.token = token;
        this.retries = retries;
        this.timeout = timeout;
        this.sleeper = sleeper;
    }

    static URI withTrailingSlash(URI uri) {
        return URI.create(uri.toString().replaceAll("/+$", "") + "/");
    }

    ObjectNode object() {
        return json.createObjectNode();
    }

    // 404, 409 (an empty repository) and 422 (not a commit) all mean "no tree here".
    Optional<JsonNode> rest(String path) {
        HttpResponse<String> response = send(() -> request(path).GET().build());
        return switch (response.statusCode()) {
            case 200 -> Optional.of(parse(response));
            case 404, 409, 422 -> Optional.empty();
            default -> throw failure(response);
        };
    }

    // GraphQL reports errors in a 200 response, beside whatever data it could resolve.
    JsonNode graphql(String query, ObjectNode variables) {
        String body = object().put("query", query).set("variables", variables).toString();
        HttpResponse<String> response = send(() -> request("graphql")
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

    // A request is built again for each attempt, so a token read again after a 401 is the one sent.
    HttpResponse<String> send(Supplier<HttpRequest> request) {
        boolean refreshed = false;
        for (int attempt = 1; ; attempt++) {
            HttpRequest built = request.get();
            HttpResponse<String> response;
            try {
                response = http.send(built, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                Duration wait = retries.afterError(e, attempt)
                        .orElseThrow(() -> new GitHubException(built.method() + " " + built.uri() + " failed", e));
                pause(wait, built);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GitHubException("interrupted calling " + built.uri(), e);
            }
            if (response.statusCode() == 401 && !refreshed && token.refresh()) {
                refreshed = true;
                continue;
            }
            ssoRequired(response);
            Optional<Duration> wait = retries.afterResponse(response.statusCode(), response.headers(), attempt,
                    Instant.now());
            if (wait.isEmpty()) {
                return response;
            }
            pause(wait.get(), built);
        }
    }

    // GitHub answers a token not authorised for an organisation's SAML SSO with a 403 and this header.
    static void ssoRequired(HttpResponse<String> response) {
        if (response.statusCode() == 403) {
            response.headers().firstValue("x-github-sso").filter(h -> h.startsWith("required"))
                    .ifPresent(h -> {
                        throw new SsoRequired(h);
                    });
        }
    }

    private void pause(Duration wait, HttpRequest request) {
        try {
            sleeper.sleep(wait);
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
                .timeout(timeout)
                .header("Authorization", "Bearer " + token.token())
                .header("Accept", "application/vnd.github+json");
    }
}
