package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import io.github.fiftieshousewife.dockerfileinventory.http.HttpLog;
import io.github.fiftieshousewife.dockerfileinventory.http.RatePacing;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GitHubSender {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubSender.class);

    private final HttpClient http;
    private final Credential token;
    private final RetryPolicy retries;
    private final Sleeper sleeper;
    private final Throttle throttle;

    GitHubSender(HttpClient http, Credential token, RetryPolicy retries, Sleeper sleeper) {
        this(http, token, retries, sleeper, Throttle.NONE);
    }

    GitHubSender(HttpClient http, Credential token, RetryPolicy retries, Sleeper sleeper, Throttle throttle) {
        this.http = http;
        this.token = token;
        this.retries = retries;
        this.sleeper = sleeper;
        this.throttle = throttle;
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
            throttle.acquire();
            long start = System.nanoTime();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Duration took = Duration.ofNanos(System.nanoTime() - start);
            throttle.record(took, response.statusCode());
            LOG.atDebug().setMessage(() -> HttpLog.call(request, response.statusCode(), took)).log();
            return Optional.of(response);
        } catch (IOException e) {
            LOG.atDebug().setMessage(() -> HttpLog.failed(request, e)).log();
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
        LOG.atDebug().setMessage(() -> HttpLog.waiting(request, wait)).log();
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("interrupted calling " + request.uri(), e);
        }
    }
}
