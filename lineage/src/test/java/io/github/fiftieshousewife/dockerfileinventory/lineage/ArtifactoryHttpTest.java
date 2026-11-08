package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        artifactory = artifactory(TokenSource.from(Map.of("ARTIFACTORY_TOKEN", "t0ken"), "ARTIFACTORY_TOKEN"));
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
                exchange.getRequestURI().getRawQuery().contains("last=")
                        ? "catalog-page2.json" : "catalog-page1.json"));

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
        route("/artifactory/api/storage/docker-local/team/app/1.0",
                exchange -> respond(exchange, 200, "storage-tag.json"));

        assertThat(artifactory.lastPushed("docker-local", "team/app", "1.0"))
                .contains(Instant.parse("2026-10-02T09:15:00Z"));
        assertThat(artifactory.lastPushed("docker-local", "team/app", "gone")).isEmpty();
    }

    private ArtifactoryHttp artifactory(TokenSource token) {
        return new ArtifactoryHttp(new ArtifactoryClient(HttpSettings.from(Map.of()).client(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/artifactory/"), token,
                new RetryPolicy(4, Duration.ofHours(1)), Duration.ofSeconds(10), wait -> { }), 2);
    }

    @Test
    void aTagsDownloadsAreReadFromItsManifestsStats() {
        route("/artifactory/api/storage/docker-local/team/app/1.0/manifest.json",
                exchange -> respond(exchange, 200, "storage-stats.json"));

        assertThat(artifactory.stats("docker-local", "team/app", "1.0"))
                .contains(new DownloadStats(42, Instant.ofEpochMilli(1_793_908_800_000L)));
    }

    @Test
    void anIndexsDownloadsAreReadFromItsListManifest() {
        route("/artifactory/api/storage/docker-local/team/app/2.0/list.manifest.json",
                exchange -> respond(exchange, 200, "storage-stats.json"));

        assertThat(artifactory.stats("docker-local", "team/app", "2.0"))
                .contains(new DownloadStats(42, Instant.ofEpochMilli(1_793_908_800_000L)));
    }

    @Test
    void aGatewayErrorIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        route("/artifactory/api/repositories", exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(502, -1);
                exchange.close();
            } else {
                respond(exchange, 200, "repositories.json");
            }
        });

        assertThat(artifactory.dockerRepositories()).containsExactly("docker-local", "base-images");
    }

    @Test
    void a401ReadsTheTokenFileAgainAndTriesOnceMore(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("token"), "old");
        TokenSource token = TokenSource.from(Map.of("ARTIFACTORY_TOKEN_FILE", file.toString()), "ARTIFACTORY_TOKEN");
        token.token();
        FileTime unchanged = Files.getLastModifiedTime(file);
        Files.writeString(file, "new");
        Files.setLastModifiedTime(file, unchanged);
        route("/artifactory/api/repositories", exchange -> {
            if ("Bearer new".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 200, "repositories.json");
            } else {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
            }
        });

        assertThat(artifactory(token).dockerRepositories()).containsExactly("docker-local", "base-images");
    }

    @Test
    void a403IsARefusalNamingWhatWasRefused() {
        route("/artifactory/api/docker/secret-local/v2/_catalog", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });

        assertThatExceptionOfType(AccessRefused.class).isThrownBy(() -> artifactory.images("secret-local"))
                .withMessageContaining("secret-local").withMessageContaining("403");
    }

    @Test
    void retriesA429AndA503AfterRetryAfterThenReturnsTheAnswer() {
        AtomicInteger calls = new AtomicInteger();
        route("/artifactory/api/repositories", exchange -> {
            switch (calls.incrementAndGet()) {
                case 1 -> slowDown(exchange, 429);
                case 2 -> slowDown(exchange, 503);
                default -> respond(exchange, 200, "repositories.json");
            }
        });

        assertThat(artifactory.dockerRepositories()).containsExactly("docker-local", "base-images");
        assertThat(calls).hasValue(3);
    }

    @Test
    void anyOtherStatusIsAnErrorNamingTheRequestAndStatusButNotTheToken() {
        route("/artifactory/api/docker/docker-local/v2/team/app/manifests/1.0",
                exchange -> respond(exchange, 500, "server-error.json"));

        assertThatExceptionOfType(ArtifactoryException.class)
                .isThrownBy(() -> artifactory.manifest("docker-local", "team/app", "1.0"))
                .withMessageContaining("GET http://127.0.0.1:")
                .withMessageContaining("/api/docker/docker-local/v2/team/app/manifests/1.0")
                .withMessageContaining("500")
                .withMessageNotContaining("t0ken");
    }

    @Test
    void unreadableJsonIsAnError() {
        route("/artifactory/api/repositories", exchange -> respond(exchange, 200, "not-json.txt"));

        assertThatExceptionOfType(ArtifactoryException.class)
                .isThrownBy(() -> artifactory.dockerRepositories())
                .withMessageContaining("/api/repositories");
    }

    @Test
    void pagingAsksForEntriesAfterTheLastOneListed() {
        assertThat(ArtifactoryHttp.after(List.of())).isEmpty();
        assertThat(ArtifactoryHttp.after(List.of("a", "team/b"))).isEqualTo("&last=team%2Fb");
    }

    @Test
    void aPagedListingStopsAtTheFirstShortPage() {
        route("/artifactory/api/docker/docker-local/v2/_catalog", exchange -> respond(exchange, 200,
                exchange.getRequestURI().getRawQuery().contains("last=")
                        ? "catalog-page2.json" : "catalog-page1.json"));

        assertThat(artifactory.paged("api/docker/docker-local/v2/_catalog", "repositories")).hasSize(3);
        assertThat(requests).hasSize(2);
    }

    @Test
    void aMissingJsonDocumentReadsAsEmpty() {
        assertThat(artifactory.getJson("api/repositories").isEmpty()).isTrue();
    }

    private static void slowDown(HttpExchange exchange, int status) throws IOException {
        exchange.getResponseHeaders().add("Retry-After", "0");
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
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
