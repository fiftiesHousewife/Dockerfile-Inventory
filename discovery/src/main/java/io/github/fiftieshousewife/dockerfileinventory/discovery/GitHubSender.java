package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import io.github.fiftieshousewife.dockerfileinventory.http.HttpLog;
import io.github.fiftieshousewife.dockerfileinventory.http.RatePacing;
import io.github.fiftieshousewife.dockerfileinventory.http.Retries;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GitHubSender {

    private static final Duration TOLD = Duration.ofSeconds(30);

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
        Attempts attempts = new Attempts(request);
        try {
            HttpResponse<String> response = Retries.send(retries, attempts::next, wait -> told(wait, attempts.last));
            RatePacing.pause(response.headers(), Instant.now()).ifPresent(wait -> pause(wait, attempts.last));
            return response;
        } catch (IOException e) {
            throw new GitHubException(attempts.last.method() + " " + attempts.last.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("interrupted calling " + attempts.last.uri(), e);
        }
    }

    private final class Attempts {

        private final Supplier<HttpRequest> request;
        private HttpRequest last;
        private boolean refreshed;

        Attempts(Supplier<HttpRequest> request) {
            this.request = request;
        }

        HttpResponse<String> next() throws IOException, InterruptedException {
            HttpResponse<String> response = exchange(built());
            if (response.statusCode() == 401 && !refreshed && token.refresh()) {
                refreshed = true;
                response = exchange(built());
            }
            ssoRequired(response);
            return response;
        }

        private HttpRequest built() {
            last = request.get();
            return last;
        }
    }

    HttpResponse<String> exchange(HttpRequest request) throws IOException, InterruptedException {
        throttle.acquire();
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Duration took = Duration.ofNanos(System.nanoTime() - start);
            throttle.record(took, response.statusCode());
            LOG.atDebug().setMessage(() -> HttpLog.call(request, response.statusCode(), took)).log();
            return response;
        } catch (IOException e) {
            LOG.atDebug().setMessage(() -> HttpLog.failed(request, e)).log();
            throw e;
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

    static void told(Duration wait, HttpRequest request) {
        if (told(wait)) {
            LOG.info(waiting(wait, Instant.now()));
        }
        LOG.atDebug().setMessage(() -> HttpLog.waiting(request, wait)).log();
    }

    void pause(Duration wait, HttpRequest request) {
        told(wait, request);
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("interrupted calling " + request.uri(), e);
        }
    }

    static boolean told(Duration wait) {
        return wait.compareTo(TOLD) >= 0;
    }

    static String waiting(Duration wait, Instant now) {
        return "discovery: waiting " + Progress.elapsed(wait) + " for GitHub, until "
                + now.plus(wait).truncatedTo(ChronoUnit.SECONDS);
    }
}
