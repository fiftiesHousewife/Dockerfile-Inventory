package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class ArtifactoryClientTest {

    private static final URI URL = URI.create("https://artifactory.example.com/artifactory/api/repositories");

    @Test
    void theBaseUrlEndsInExactlyOneSlash() {
        assertThat(ArtifactoryClient.withTrailingSlash(URI.create("https://a.example.com/artifactory")))
                .isEqualTo(URI.create("https://a.example.com/artifactory/"));
        assertThat(ArtifactoryClient.withTrailingSlash(URI.create("https://a.example.com/artifactory//")))
                .isEqualTo(URI.create("https://a.example.com/artifactory/"));
    }

    @Test
    void a200IsTheResponseAndA404IsNothing() {
        Response ok = new Response(200, "{}");

        assertThat(ArtifactoryClient.found(URL, ok)).contains(ok);
        assertThat(ArtifactoryClient.found(URL, new Response(404, ""))).isEmpty();
    }

    @Test
    void anyOtherStatusIsAnErrorNamingTheUrlStatusAndTheStartOfTheBody() {
        assertThatExceptionOfType(ArtifactoryException.class)
                .isThrownBy(() -> ArtifactoryClient.found(URL, new Response(500, "x".repeat(400))))
                .withMessage("GET " + URL + " returned 500: " + "x".repeat(300));
    }

    @Test
    void aLongBodyIsCutToThreeHundredCharacters() {
        assertThat(ArtifactoryClient.abbreviated("y".repeat(301))).hasSize(300);
        assertThat(ArtifactoryClient.abbreviated("short")).isEqualTo("short");
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
