package io.github.fiftieshousewife.dockerfileinventory.lineage;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpLog;
import io.github.fiftieshousewife.dockerfileinventory.http.Retries;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final Throttle throttle;

    ArtifactoryClient(HttpClient http, URI base, TokenSource token, RetryPolicy retries, Duration timeout) {
        this(http, base, token, retries, timeout, Throttle.NONE);
    }

    // The client follows redirects: Artifactory can redirect blob downloads to its file store.
    ArtifactoryClient(HttpClient http, URI base, TokenSource token, RetryPolicy retries, Duration timeout,
            Throttle throttle) {
        this.http = http;
        this.base = withTrailingSlash(base);
        this.token = token;
        this.retries = retries;
        this.timeout = timeout;
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

    HttpResponse<String> send(Supplier<HttpRequest> request) {
        AtomicReference<HttpRequest> last = new AtomicReference<>();
        AtomicBoolean refreshed = new AtomicBoolean();
        try {
            return Retries.send(retries, () -> {
                HttpResponse<String> response = exchange(built(request, last));
                return response.statusCode() == 401 && !refreshed.getAndSet(true) && token.refresh()
                        ? exchange(built(request, last)) : response;
            }, wait -> LOG.atDebug().setMessage(() -> HttpLog.waiting(last.get(), wait)).log());
        } catch (IOException e) {
            throw new ArtifactoryException("GET " + last.get().uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactoryException("interrupted calling " + last.get().uri(), e);
        }
    }

    private static HttpRequest built(Supplier<HttpRequest> request, AtomicReference<HttpRequest> last) {
        last.set(request.get());
        return last.get();
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
}
