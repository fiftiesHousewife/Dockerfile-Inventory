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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineageDemoTest {

    private HttpServer server;
    private JcrApi jcr;
    private final List<String> calls = new ArrayList<>();
    private boolean eulaPending;
    private final JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();

    @BeforeEach
    void aReadyJcr() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String call = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            calls.add(call);
            reply(exchange, switch (call) {
                case "GET /artifactory/api/system/ping" -> "OK";
                case "GET /artifactory/api/repositories" -> "[{\"key\":\"docker-local\"}]";
                case "GET /artifactory/api/docker/docker-local/v2/" -> eulaPending ? "accept the EULA" : "{}";
                case "POST /access/api/v1/tokens" -> "{\"access_token\":\"t1\"}";
                default -> "{}";
            });
        });
        server.start();
        jcr = new JcrApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "password",
                HttpClient.newHttpClient());
        docker.answers.put("docker ps -q -f name=^", new Commands.Result(0, "running\n"));
        docker.answers.put("docker port inventory-jcr", new Commands.Result(0, "0.0.0.0:8082\n"));
        docker.answers.put("docker inspect -f {{.Config.Image}}", new Commands.Result(0, "jcr:7.77.17\n"));
        docker.answers.put("docker info --format {{.MemTotal}}", new Commands.Result(0, "8589934592\n"));
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LineageDemo demo(Map<String, String> env) {
        return new LineageDemo(docker, jcr, new JcrContainers(docker, "jcr:7.77.17", "postgres:16.4", 8082),
                LineageDemo.Waiter.immediate(), env);
    }

    @Test
    void aRunningJcrIsReusedAndGivesTheRepositoryAndAReadToken() {
        LineageDemo.Ready ready = demo(Map.of()).registry();

        assertAll(
                () -> assertThat(ready).isEqualTo(new LineageDemo.Ready("docker-local", "t1")),
                () -> assertThat(docker.asked).noneMatch(line -> line.startsWith("docker run")));
    }

    @Test
    void thePendingLicenceAgreementStopsTheDemoUnlessThePersonAccepted() {
        eulaPending = true;

        assertAll(
                () -> assertThatIllegalStateException().isThrownBy(() -> demo(Map.of()).registry())
                        .withMessageContaining("ACCEPT_JCR_EULA=yes"),
                () -> {
                    demo(Map.of("ACCEPT_JCR_EULA", "yes")).registry();
                    assertThat(calls).contains("POST /artifactory/ui/jcr/eula/accept");
                });
    }

    @Test
    void dockerWithLessThanFourGigabytesIsRefusedBeforeAnythingStarts() {
        docker.answers.put("docker info --format {{.MemTotal}}", new Commands.Result(0, "2147483648\n"));

        assertThatIllegalStateException().isThrownBy(() -> demo(Map.of()).registry())
                .withMessageStartingWith("Docker may use 2 GB; JCR needs 4 or more");
    }

    @Test
    void aWaitGivesUpAfterItsAttemptsSaying() {
        assertThatIllegalStateException().isThrownBy(() -> LineageDemo.Waiter.immediate().until("JCR", () -> false))
                .withMessage("JCR did not come up");
    }

    @Test
    void withAMirrorListThePublishedImagesAreCopiedInsteadOfBuilt(@TempDir Path out) throws IOException {
        Path mirror = Files.writeString(out.resolve("mirror.txt"), "quay.io/okd/tools:5 okd/tools:5\n");

        demo(Map.of("MIRROR", mirror.toString())).images(out, out, out.resolve("build.log"),
                new LineageDemo.Ready("docker-local", "t1"));

        assertThat(docker.asked).anyMatch(line -> line.contains(" copy -q --preserve-digests ")
                && line.endsWith("docker://inventory-jcr:8082/docker-local/okd/tools:5"));
    }

    @Test
    void withoutOneThePlansChainIsWrittenClonedAndBuilt(@TempDir Path out) throws IOException {
        Path discovery = Files.createDirectories(out.resolve("discovery"));
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/b1"), "FROM quay.io/centos/centos:stream9\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), "{\"repository\":\"sclorg/s2i-base-container\","
                + "\"commitSha\":\"c1\",\"path\":\"core/Dockerfile.c9s\",\"blobSha\":\"b1\"}\n");

        demo(Map.of()).images(discovery, out, out.resolve("build.log"), new LineageDemo.Ready("docker-local", "t1"));

        assertAll(
                () -> assertThat(out.resolve("plan.txt.ordered")).content().startsWith("sclorg/s2i-core-c9s:c9s\t"),
                () -> assertThat(docker.asked).anyMatch(line -> line.startsWith("git clone")),
                () -> assertThat(docker.asked).anyMatch(line -> line.contains("buildah build")));
    }
}
