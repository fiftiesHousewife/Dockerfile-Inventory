package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
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
    private final List<String> accepts = new ArrayList<>();

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

    @Test
    void pagesTagsAndTreatsNullTagsAndAMissingImageAsNoTags() {
        route("/artifactory/api/docker/docker-local/v2/team/app/tags/list", exchange -> respond(exchange, 200,
                exchange.getRequestURI().getRawQuery().contains("last=") ? "tags-page2.json" : "tags-page1.json"));
        route("/artifactory/api/docker/docker-local/v2/team/empty/tags/list",
                exchange -> respond(exchange, 200, "tags-null.json"));
        route("/artifactory/api/docker/docker-local/v2/team/gone/tags/list",
                exchange -> respond(exchange, 404, "name-unknown.json"));

        assertThat(artifactory.tags("docker-local", "team/app")).containsExactly("1.0", "1.1", "latest");
        assertThat(artifactory.tags("docker-local", "team/empty")).isEmpty();
        assertThat(artifactory.tags("docker-local", "team/gone")).isEmpty();
        assertThat(requests).contains("GET /artifactory/api/docker/docker-local/v2/team/app/tags/list?n=2&last=1.1");
    }

    @Test
    void fetchesAManifestAcceptingEveryManifestAndIndexTypeWithItsMediaTypeAndDigest() {
        route("/artifactory/api/docker/docker-local/v2/team/app/manifests/1.0", exchange -> {
            accepts.add(exchange.getRequestHeaders().getFirst("Accept"));
            exchange.getResponseHeaders().add("Docker-Content-Digest", "sha256:" + "a".repeat(64));
            respond(exchange, 200, "manifest-list.json", "application/vnd.docker.distribution.manifest.list.v2+json");
        });
        route("/artifactory/api/docker/docker-local/v2/team/app/manifests/gone",
                exchange -> respond(exchange, 404, "name-unknown.json"));

        assertThat(artifactory.manifest("docker-local", "team/app", "1.0")).hasValueSatisfying(m -> {
            assertThat(m.mediaType()).isEqualTo("application/vnd.docker.distribution.manifest.list.v2+json");
            assertThat(m.digest()).isEqualTo("sha256:" + "a".repeat(64));
            assertThat(m.body()).contains("\"architecture\": \"amd64\"");
        });
        assertThat(artifactory.manifest("docker-local", "team/app", "gone")).isEmpty();
        assertThat(accepts.getFirst()).contains(
                "application/vnd.docker.distribution.manifest.v2+json",
                "application/vnd.docker.distribution.manifest.list.v2+json",
                "application/vnd.oci.image.manifest.v1+json",
                "application/vnd.oci.image.index.v1+json");
    }

    @Test
    void fetchesABlobAsTextFollowingARedirectAndGivesEmptyForAMissingOne() {
        String digest = "sha256:" + "c".repeat(64);
        route("/artifactory/api/docker/docker-local/v2/team/app/blobs/" + digest, exchange -> {
            exchange.getResponseHeaders().add("Location", "/filestore/config");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        route("/filestore/config", exchange -> respond(exchange, 200, "config.json"));
        route("/artifactory/api/docker/docker-local/v2/team/app/blobs/sha256:gone",
                exchange -> respond(exchange, 404, "name-unknown.json"));

        assertThat(artifactory.blob("docker-local", "team/app", digest)).hasValueSatisfying(
                text -> assertThat(text).contains("\"diff_ids\""));
        assertThat(artifactory.blob("docker-local", "team/app", "sha256:gone")).isEmpty();
    }

    @Test
    void readsLastPushFromTheStorageApi() {
        route("/artifactory/api/storage/docker-local/team/app/1.0", exchange -> respond(exchange, 200, "storage-tag.json"));

        assertThat(artifactory.lastPushed("docker-local", "team/app", "1.0"))
                .contains(Instant.parse("2026-10-02T09:15:00Z"));
        assertThat(artifactory.lastPushed("docker-local", "team/app", "gone")).isEmpty();
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
        respond(exchange, status, fixture, "application/json");
    }

    private static void respond(HttpExchange exchange, int status, String fixture, String contentType)
            throws IOException {
        byte[] body = fixture(fixture);
        exchange.getResponseHeaders().add("Content-Type", contentType);
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
