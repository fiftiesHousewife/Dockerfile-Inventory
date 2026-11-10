package io.github.fiftieshousewife.dockerfileinventory.lineage;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpLog;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ArtifactoryClient {

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactoryClient.class);

    private static final int ABBREVIATED_LENGTH = 300;

    private final HttpClient http;
    private final URI base;
    private final TokenSource token;
    private final RetryPolicy retries;
    private final Duration timeout;
    private final Sleeper sleeper;
    private final Throttle throttle;

    ArtifactoryClient(HttpClient http, URI base, TokenSource token, RetryPolicy retries, Duration timeout,
            Sleeper sleeper) {
        this(http, base, token, retries, timeout, sleeper, Throttle.NONE);
    }

    // The client follows redirects: Artifactory can redirect blob downloads to its file store.
    ArtifactoryClient(HttpClient http, URI base, TokenSource token, RetryPolicy retries, Duration timeout,
            Sleeper sleeper, Throttle throttle) {
        this.http = http;
        this.base = withTrailingSlash(base);
        this.token = token;
        this.retries = retries;
        this.timeout = timeout;
        this.sleeper = sleeper;
        this.throttle = throttle;
    }

    static URI withTrailingSlash(URI base) {
        return URI.create(base.toString().replaceAll("/+$", "") + "/");
    }

    Optional<HttpResponse<String>> get(String path, String accept) {
        Supplier<HttpRequest> request = () -> HttpRequest.newBuilder(base.resolve(path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token.token())
                .header("Accept", accept)
                .GET()
                .build();
        return found(base.resolve(path), send(request));
    }

    // A 404 means the image, tag or blob no longer exists; a 401 or 403 that the token cannot read it.
    static Optional<HttpResponse<String>> found(URI uri, HttpResponse<String> response) {
        return switch (response.statusCode()) {
            case 200 -> Optional.of(response);
            case 404 -> Optional.empty();
            case 401, 403 -> throw new AccessRefused("GET %s returned %d: the token may not read it".formatted(
                    uri, response.statusCode()));
            default -> throw new ArtifactoryException("GET %s returned %d: %s".formatted(
                    uri, response.statusCode(), abbreviated(response.body())));
        };
    }

    static String abbreviated(String body) {
        return body.length() > ABBREVIATED_LENGTH ? body.substring(0, ABBREVIATED_LENGTH) : body;
    }

    // Each attempt is built afresh, so a token read again after a 401 is the one sent.
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
            Optional<Duration> wait = retries.afterResponse(response.statusCode(), response.headers(), attempt,
                    Instant.now());
            if (wait.isEmpty()) {
                return response;
            }
            pause(wait.get(), built);
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
                    .orElseThrow(() -> new ArtifactoryException("GET " + request.uri() + " failed", e)), request);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactoryException("interrupted calling " + request.uri(), e);
        }
    }

    void pause(Duration wait, HttpRequest request) {
        LOG.atDebug().setMessage(() -> HttpLog.waiting(request, wait)).log();
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactoryException("interrupted calling " + request.uri(), e);
        }
    }
}
