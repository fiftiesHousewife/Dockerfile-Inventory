package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class ArtifactoryClientTest {

    private static final URI URL = URI.create("https://artifactory.example.com/artifactory/api/repositories");

    @Test
    void theBaseUrlEndsInExactlyOneSlash() {
        assertThat(Stream.of("https://a.example.com/artifactory", "https://a.example.com/artifactory//")
                .map(base -> ArtifactoryClient.withTrailingSlash(URI.create(base))))
                .containsOnly(URI.create("https://a.example.com/artifactory/"));
    }

    @Test
    void a200IsTheResponseAndA404IsNothing() {
        Response ok = new Response(200, "{}");

        assertAll(() -> assertThat(ArtifactoryClient.found(URL, ok)).contains(ok),
                () -> assertThat(ArtifactoryClient.found(URL, new Response(404, ""))).isEmpty());
    }

    @Test
    void anyOtherStatusIsAnErrorNamingTheUrlStatusAndTheStartOfTheBody() {
        assertThatExceptionOfType(ArtifactoryException.class)
                .isThrownBy(() -> ArtifactoryClient.found(URL, new Response(500, "x".repeat(400))))
                .withMessage("GET " + URL + " returned 500: " + "x".repeat(300));
    }

    @Test
    void aLongBodyIsCutToThreeHundredCharacters() {
        assertAll(() -> assertThat(ArtifactoryClient.abbreviated("y".repeat(301))).hasSize(300),
                () -> assertThat(ArtifactoryClient.abbreviated("short")).isEqualTo("short"));
    }

    @Test
    void a401Or403IsRefusedAccessNamingTheUrl() {
        assertThatExceptionOfType(AccessRefused.class)
                .isThrownBy(() -> ArtifactoryClient.found(URL, new Response(403, "")))
                .withMessage("GET " + URL + " returned 403: the token may not read it");
    }

    @Test
    void anUnreachableServerIsTriedAgainThenFailsNamingTheUrl() throws IOException {
        URI closed = closedPort();

        assertThatExceptionOfType(ArtifactoryException.class)
                .isThrownBy(() -> client(2).send(() -> HttpRequest.newBuilder(closed).build()))
                .withMessage("GET " + closed + " failed");
    }

    private static ArtifactoryClient client(int attempts) {
        return new ArtifactoryClient(HttpClient.newHttpClient(), URL, TokenSource.from(Map.of("T", "t"), "T"),
                new RetryPolicy(attempts, Duration.ofHours(1), Duration.ZERO), Duration.ofSeconds(5));
    }

    private static URI closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/");
        }
    }

    private record Response(int statusCode, String body) implements HttpResponse<String> {

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URL).build();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URL;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
