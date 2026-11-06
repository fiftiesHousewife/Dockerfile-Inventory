package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactoryHttpTest {

    private HttpServer server;
    private ArtifactoryHttp artifactory;
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        artifactory = new ArtifactoryHttp(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/artifactory/"), "t0ken", 2);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void listsLocalDockerRepositoriesByKeyWithTheBearerToken() {
        route("/artifactory/api/repositories", exchange -> respond(exchange, 200, "repositories.json"));

        assertThat(artifactory.dockerRepositories()).containsExactly("docker-local", "base-images");
        assertThat(requests).containsExactly("GET /artifactory/api/repositories?type=local&packageType=docker");
        assertThat(authorizations).containsOnly("Bearer t0ken");
    }

    @Test
    void pagesTheCatalogueWithNAndLastUntilAShortPage() {
        route("/artifactory/api/docker/docker-local/v2/_catalog", exchange -> respond(exchange, 200,
                exchange.getRequestURI().getRawQuery().contains("last=") ? "catalog-page2.json" : "catalog-page1.json"));

        assertThat(artifactory.images("docker-local")).containsExactly("team/app", "team/worker", "ubi9/ubi-minimal");
        assertThat(requests).containsExactly(
                "GET /artifactory/api/docker/docker-local/v2/_catalog?n=2",
                "GET /artifactory/api/docker/docker-local/v2/_catalog?n=2&last=team%2Fworker");
    }

    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private void route(String path, Handler handler) {
        server.createContext(path, exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            handler.handle(exchange);
        });
    }

    private static void respond(HttpExchange exchange, int status, String fixture) throws IOException {
        byte[] body = fixture(fixture);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] fixture(String name) {
        try (InputStream in = ArtifactoryHttpTest.class.getResourceAsStream("/fixtures/artifactory/" + name)) {
            return Objects.requireNonNull(in, name).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
