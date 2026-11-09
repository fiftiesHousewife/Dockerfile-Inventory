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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoRegistryTest {

    private HttpServer server;
    private JcrApi jcr;
    private final List<String> calls = new ArrayList<>();
    private boolean eulaPending;
    private boolean noRepositories;
    private int adminStatus = 200;
    private final JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();

    @BeforeEach
    void aReadyJcr() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String call = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            calls.add(call);
            String authorization = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = call.equals("GET /artifactory/api/repositories") && authorization.startsWith("Basic ")
                    && exchange.getRequestURI().getQuery() == null ? adminStatus : 200;
            reply(exchange, status, switch (call) {
                case "GET /artifactory/api/system/ping" -> "OK";
                case "GET /artifactory/api/repositories" -> noRepositories ? "[]" : "[{\"key\":\"docker-local\"}]";
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

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private DemoRegistry registry(Map<String, String> env) {
        return new DemoRegistry(jcr, new JcrContainers(docker, "jcr:7.77.17", "postgres:16.4", 8082),
                Waiter.immediate(), env);
    }

    @Test
    void aRunningJcrIsReusedAndGivesTheRepositoryAndAReadToken() {
        DemoRegistry.Ready ready = registry(Map.of()).start();

        assertAll(
                () -> assertThat(ready).isEqualTo(new DemoRegistry.Ready("docker-local", "t1")),
                () -> assertThat(docker.asked).noneMatch(line -> line.startsWith("docker run")));
    }

    @Test
    void thePendingLicenceAgreementStopsTheDemoUnlessThePersonAccepted() {
        eulaPending = true;

        assertThatIllegalStateException().isThrownBy(() -> registry(Map.of()).acceptAgreement("docker-local"))
                .withMessageContaining("ACCEPT_JCR_EULA=yes");
    }

    @Test
    void anAgreementThePersonAcceptedIsAcceptedThroughTheApi() {
        eulaPending = true;

        registry(Map.of("ACCEPT_JCR_EULA", "yes")).acceptAgreement("docker-local");

        assertThat(calls).contains("POST /artifactory/ui/jcr/eula/accept");
    }

    @Test
    void anAgreementAlreadyAcceptedIsLeftAlone() {
        registry(Map.of()).acceptAgreement("docker-local");

        assertThat(calls).doesNotContain("POST /artifactory/ui/jcr/eula/accept");
    }

    @Test
    void dockerWithLessThanFourGigabytesIsRefusedBeforeAnythingStarts() {
        docker.answers.put("docker info --format {{.MemTotal}}", new Commands.Result(0, "2147483648\n"));

        assertThatIllegalStateException().isThrownBy(() -> registry(Map.of()).requireMemory())
                .withMessageStartingWith("Docker may use 2 GB; JCR needs 4 or more");
    }

    @Test
    void aRefusedAdminLoginIsExplained() {
        adminStatus = 401;

        assertThatIllegalStateException().isThrownBy(() -> registry(Map.of()).requireAdmin())
                .withMessageStartingWith("admin login refused");
    }

    @Test
    void aRepositoryJcrWillNotCreateIsLeftToThePersonInTheUiAfterAskingTheWizard() {
        noRepositories = true;

        assertAll(
                () -> assertThatIllegalStateException().isThrownBy(() -> registry(Map.of()).repository())
                        .withMessageContaining("Create a Repository > Local > Docker, key 'docker-local'"),
                () -> assertThat(docker.asked).contains("docker restart inventory-jcr"));
    }

    @Test
    void aTokenGivenIsUsedInsteadOfMakingOne() {
        assertThat(registry(Map.of("ARTIFACTORY_TOKEN", "mine")).readToken()).isEqualTo("mine");
    }
}
