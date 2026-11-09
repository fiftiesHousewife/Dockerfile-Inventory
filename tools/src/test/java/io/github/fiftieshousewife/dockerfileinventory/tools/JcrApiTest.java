package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JcrApiTest {

    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:password"
            .getBytes(StandardCharsets.UTF_8));

    private HttpServer server;
    private JcrApi jcr;
    private final List<String> calls = new ArrayList<>();
    private String repositories = "[{\"key\":\"docker-wizard\"},{\"key\":\"docker-local\"}]";
    private boolean accessApiWorks = true;

    @BeforeEach
    void aLoopbackJcr() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String call = exchange.getRequestMethod() + " " + exchange.getRequestURI();
            calls.add(call);
            String auth = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, switch (call) {
                case "GET /artifactory/api/system/ping" -> "OK";
                case "GET /artifactory/api/repositories?type=local&packageType=docker" ->
                        auth.equals(ADMIN) ? repositories : "{\"errors\":[]}";
                case "GET /artifactory/api/docker/docker-local/v2/" -> "{\"errors\":[{\"message\":\"Please accept "
                        + "the EULA first\"}]}";
                case "POST /access/api/v1/tokens" -> accessApiWorks ? "{\"access_token\":\"new\"}" : "{}";
                case "POST /artifactory/api/security/token" -> "{\"access_token\":\"old\"}";
                default -> "";
            }, call.equals("GET /artifactory/api/repositories") && !auth.equals("Bearer new")
                    && !auth.equals("Bearer old") ? 401 : 200);
        });
        server.start();
        jcr = new JcrApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "password",
                HttpClient.newHttpClient());
    }

    private static void reply(HttpExchange exchange, String body, int status) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void jcrAnswersAPing() {
        assertThat(jcr.answers()).isTrue();
    }

    @Test
    void theRepositoryIsDockerLocalWhenThereIsOneElseTheFirstLocalDockerRepository() {
        assertAll(
                () -> assertThat(jcr.repository("docker-local")).contains("docker-local"),
                () -> {
                    repositories = "[{\"key\":\"docker-wizard\"}]";
                    assertThat(jcr.repository("docker-local")).contains("docker-wizard");
                },
                () -> {
                    repositories = "{\"errors\":[]}";
                    assertThat(jcr.repository("docker-local")).isEmpty();
                });
    }

    @Test
    void aRepositoryIsCreatedThroughTheApiAsALocalDockerOne() {
        jcr.create("docker-local");

        assertThat(calls).contains("PUT /artifactory/api/repositories/docker-local");
    }

    @Test
    void theLicenceAgreementIsPendingWhileTheDockerApiAsksForIt() {
        assertThat(jcr.agreementPending("docker-local")).isTrue();
    }

    @Test
    void aReadTokenComesFromTheAccessApiOrElseTheOlderOne() {
        assertAll(
                () -> assertThat(jcr.token()).isEqualTo("new"),
                () -> {
                    accessApiWorks = false;
                    assertThat(jcr.token()).isEqualTo("old");
                });
    }
}
