package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.RatePacing;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

final class GitHubSender {

    private final HttpClient http;
    private final TokenSource token;
    private final RetryPolicy retries;
    private final Sleeper sleeper;

    GitHubSender(HttpClient http, TokenSource token, RetryPolicy retries, Sleeper sleeper) {
        this.http = http;
        this.token = token;
        this.retries = retries;
        this.sleeper = sleeper;
    }

    // A request is built again for each attempt, so a token read again after a 401 is the one sent.
    HttpResponse<String> send(Supplier<HttpRequest> request) {
        boolean refreshed = false;
        for (int attempt = 1; ; attempt++) {
            HttpRequest built = request.get();
            Optional<HttpResponse<String>> answered = exchange(built, attempt);
            if (answered.isEmpty()) {
                continue;
            }
            HttpResponse<String> response = answered.get();
            if (response.statusCode() == 401 && !refreshed && token.refresh()) {
                refreshed = true;
                continue;
            }
            ssoRequired(response);
            if (finished(response, built, attempt)) {
                return response;
            }
        }
    }

    Optional<HttpResponse<String>> exchange(HttpRequest request, int attempt) {
        try {
            return Optional.of(http.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException e) {
            pause(retries.afterError(e, attempt)
                    .orElseThrow(() -> new GitHubException(request.method() + " " + request.uri() + " failed", e)),
                    request);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("interrupted calling " + request.uri(), e);
        }
    }

    boolean finished(HttpResponse<String> response, HttpRequest request, int attempt) {
        Optional<Duration> retryAfter = retries.afterResponse(response.statusCode(), response.headers(), attempt,
                Instant.now());
        retryAfter.or(() -> RatePacing.pause(response.headers(), Instant.now()))
                .ifPresent(wait -> pause(wait, request));
        return retryAfter.isEmpty();
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

    void pause(Duration wait, HttpRequest request) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("interrupted calling " + request.uri(), e);
        }
    }
}
