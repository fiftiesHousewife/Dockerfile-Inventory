package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

final class ArtifactoryClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 6;
    private static final int ABBREVIATED_LENGTH = 300;

    private final HttpClient http;
    private final URI base;
    private final String token;

    ArtifactoryClient(URI base, String token) {
        // Artifactory can redirect blob downloads to its file store.
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.base = withTrailingSlash(base);
        this.token = token;
    }

    static URI withTrailingSlash(URI base) {
        return URI.create(base.toString().replaceAll("/+$", "") + "/");
    }

    Optional<HttpResponse<String>> get(String path, String accept) {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", accept)
                .GET()
                .build();
        return found(request.uri(), send(request));
    }

    // A 404 means the image, tag or blob no longer exists.
    static Optional<HttpResponse<String>> found(URI uri, HttpResponse<String> response) {
        return switch (response.statusCode()) {
            case 200 -> Optional.of(response);
            case 404 -> Optional.empty();
            default -> throw new ArtifactoryException("GET %s returned %d: %s".formatted(
                    uri, response.statusCode(), abbreviated(response.body())));
        };
    }

    static String abbreviated(String body) {
        return body.length() > ABBREVIATED_LENGTH ? body.substring(0, ABBREVIATED_LENGTH) : body;
    }

    HttpResponse<String> send(HttpRequest request) {
        try {
            for (int attempt = 1; ; attempt++) {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Optional<Duration> wait = Retry.waitBeforeRetry(response.statusCode(), response.headers());
                if (wait.isEmpty() || attempt == MAX_ATTEMPTS) {
                    return response;
                }
                Thread.sleep(wait.get());
            }
        } catch (IOException e) {
            throw new ArtifactoryException("GET " + request.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactoryException("interrupted calling " + request.uri(), e);
        }
    }
}
