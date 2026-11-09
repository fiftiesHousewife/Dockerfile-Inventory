package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JcrRecordTest {

    private static final Path RECORDED = Path.of("../lineage/src/test/resources/fixtures/artifactory-jcr");

    @TempDir
    Path out;

    private HttpServer server;
    private URI api;

    // JCR as it answered when the fixtures were recorded: every reply index.tsv lists, byte for byte.
    @BeforeEach
    void aReplayedJcr() throws IOException {
        Map<String, String[]> replies = new HashMap<>();
        Files.readAllLines(RECORDED.resolve("index.tsv")).forEach(line -> {
            String[] fields = line.split("\t", -1);
            replies.put(fields[0], fields);
        });
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/artifactory/api/system/ping", exchange -> reply(exchange, 200, "OK".getBytes(
                StandardCharsets.UTF_8), "text/plain", ""));
        server.createContext("/artifactory/api/security/token", exchange -> reply(exchange, 200,
                "{\"access_token\":\"t1\"}".getBytes(StandardCharsets.UTF_8), "application/json", ""));
        server.createContext("/artifactory/api/docker", exchange -> replay(exchange, replies));
        server.createContext("/artifactory/api/repositories", exchange -> replay(exchange, replies));
        server.createContext("/artifactory/api/storage", exchange -> replay(exchange, replies));
        server.start();
        api = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/artifactory/");
    }

    private static void replay(HttpExchange exchange, Map<String, String[]> replies) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String[] reply = replies.get(exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query));
        boolean authorised = "Bearer t1".equals(exchange.getRequestHeaders().getFirst("Authorization"));
        if (reply == null || !authorised) {
            reply(exchange, 404, new byte[0], "text/plain", "");
            return;
        }
        reply(exchange, Integer.parseInt(reply[2]), Files.readAllBytes(RECORDED.resolve(reply[1])), reply[3],
                reply[4]);
    }

    private static void reply(HttpExchange exchange, int status, byte[] body, String type, String digest)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        if (!digest.isEmpty()) {
            exchange.getResponseHeaders().add("Docker-Content-Digest", digest);
        }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void everyReplyLineageAsksForIsRecordedByteForByteWithItsIndex() throws IOException {
        new JcrRecord(api, "password", out, HttpClient.newHttpClient()).run();

        List<String> recorded;
        try (Stream<Path> files = Files.list(RECORDED)) {
            recorded = files.map(file -> String.valueOf(file.getFileName())).filter(name -> !name.equals("README.md"))
                    .sorted().toList();
        }
        assertAll(recorded.stream().map(name -> () -> assertThat(out.resolve(name)).as(name)
                .hasSameBinaryContentAs(RECORDED.resolve(name))));
    }

    @Test
    void aJcrThatDoesNotAnswerIsRefusedNamingWhere() {
        server.stop(0);

        assertThatIllegalStateException().isThrownBy(() -> new JcrRecord(api, "password", out,
                HttpClient.newHttpClient()).ping()).withMessageStartingWith("JCR is not answering at " + api);
    }

    @Test
    void aJcrThatGivesNoTokenIsRefused() {
        server.removeContext("/artifactory/api/security/token");
        server.createContext("/artifactory/api/security/token", exchange -> reply(exchange, 200,
                "{}".getBytes(StandardCharsets.UTF_8), "application/json", ""));

        assertThatIllegalStateException().isThrownBy(() -> new JcrRecord(api, "password", out,
                HttpClient.newHttpClient()).token()).withMessage("no token from JCR");
    }

    @Test
    void aTagWhoseManifestNamesNoConfigHasNoConfigRecorded() {
        new JcrRecord(api, "password", out, HttpClient.newHttpClient()).tag(new RecordedReplies(api, out,
                HttpClient.newHttpClient(), "t1"), "docker-local", "missing", "1");

        assertThat(out.resolve("config-missing_1.json")).doesNotExist();
    }

    @Test
    void theEnvironmentsSettingsMakeARecorder() {
        assertThat(JcrRecord.of(Map.of("JCR_PORT", "18082"))).isNotNull();
    }
}
