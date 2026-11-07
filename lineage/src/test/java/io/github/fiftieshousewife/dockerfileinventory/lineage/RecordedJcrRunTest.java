package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RecordedJcrRunTest {

    private static final Path RECORDED = Path.of("src/test/resources/fixtures/artifactory-jcr");
    private static final Path RUN = Path.of("src/test/resources/fixtures/jcr-run");
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;

    @BeforeEach
    void serveTheRecordedReplies() throws IOException {
        Map<String, String[]> replies = new HashMap<>();
        Files.readAllLines(RECORDED.resolve("index.tsv")).stream().map(l -> l.split("\t", -1))
                .forEach(r -> replies.put(r[0], r));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String[] reply = replies.get(exchange.getRequestURI().toString());
            if (reply == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(RECORDED.resolve(reply[1]));
            exchange.getResponseHeaders().add("Content-Type", reply[3]);
            if (!reply[4].isEmpty()) {
                exchange.getResponseHeaders().add("Docker-Content-Digest", reply[4]);
            }
            exchange.sendResponseHeaders(Integer.parseInt(reply[2]), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void theRecordedRunGivesEveryImageItsParentAndItsOwnDockerfiles(@TempDir Path out) {
        Artifactory artifactory = new ArtifactoryHttp(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/artifactory/"),
                HttpSettings.from(Map.of()),
                TokenSource.from(Map.of("ARTIFACTORY_TOKEN", "t0ken"), "ARTIFACTORY_TOKEN"));

        new Inventory(artifactory, new LineageFiles(out), 1).run();
        new Analysis(out, RUN.resolve("facts"), RUN.resolve("discovery")).run();

        Map<String, String> names = new HashMap<>();
        lines(out.resolve("images.jsonl")).forEach(r -> names.put(r.path("configDigest").asString(),
                r.path("image").asString() + ":" + r.path("tag").asString()));
        Map<String, String> parents = new TreeMap<>();
        lines(out.resolve("lineage.jsonl")).forEach(r -> parents.put(names.get(r.path("configDigest").asString()),
                r.path("parent").path("kind").asString().equals("Of")
                        ? names.get(r.path("parent").path("configDigest").asString())
                        : r.path("parent").path("kind").asString()));
        Map<String, String> links = new TreeMap<>();
        lines(out.resolve("links.jsonl")).forEach(r -> links.put(names.get(r.path("configDigest").asString()),
                r.path("tier").asString() + " " + String.join(",", StreamSupport
                        .stream(r.path("blobShas").spliterator(), false).map(b -> b.asString().substring(0, 10))
                        .sorted().toList())));

        assertThat(parents).isEqualTo(Map.of(
                "centos/centos:stream9", "Root",
                "sclorg/s2i-core-c9s:c9s", "centos/centos:stream9",
                "sclorg/s2i-base-c9s:c9s", "sclorg/s2i-core-c9s:c9s",
                "sclorg/nginx-container-1-24-c9s:c9s", "sclorg/s2i-core-c9s:c9s",
                "sclorg/s2i-python-container-3-12-c9s:c9s", "sclorg/s2i-base-c9s:c9s"));
        assertThat(links).isEqualTo(Map.of(
                "centos/centos:stream9", "NONE ",
                "sclorg/s2i-core-c9s:c9s", "PROBABLE 1723284a86",
                "sclorg/s2i-base-c9s:c9s", "PROBABLE 84d379564c",
                "sclorg/nginx-container-1-24-c9s:c9s", "PROBABLE 6070555cb3,6add3832a5,b2c5980342,d69ccd7230",
                "sclorg/s2i-python-container-3-12-c9s:c9s", "PROBABLE 22461d2436"));
    }

    private static List<JsonNode> lines(Path file) {
        try {
            return Files.readAllLines(file).stream().filter(l -> !l.isBlank()).map(JSON::readTree).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
