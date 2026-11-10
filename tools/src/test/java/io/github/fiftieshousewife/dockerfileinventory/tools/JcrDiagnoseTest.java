package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class JcrDiagnoseTest {

    @Test
    void aTokenInAReplyIsHiddenAndTheReplyCutToItsStart() {
        assertAll(
                () -> assertThat(JcrDiagnose.redacted("{\"token\" : \"abc\", \"access_token\":\"def\"}\nrest"))
                        .isEqualTo("{\"token\":\"(hidden)\", \"access_token\":\"(hidden)\"} rest"),
                () -> assertThat(JcrDiagnose.redacted("x".repeat(300))).hasSize(200));
    }

    @Test
    void everySectionIsPrintedWithWhatItsCommandsSaid() {
        JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();
        docker.answers.put("docker image inspect", new Commands.Result(0, "arm64\n"));
        ByteArrayOutputStream printed = new ByteArrayOutputStream();

        new JcrDiagnose(docker, URI.create("http://127.0.0.1:1/"), "password", HttpClient.newHttpClient(), "jcr:7",
                new PrintStream(printed, true, StandardCharsets.UTF_8)).run();

        String report = printed.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertThat(report).contains("== image architecture", "arm64", "== Docker API"),
                () -> assertThat(docker.asked).contains("docker exec inventory-jcr ls /var/opt/jfrog/artifactory/log"));
    }

    private static JcrDiagnose diagnose(JcrContainersTest.Recorded docker, ByteArrayOutputStream printed) {
        return new JcrDiagnose(docker, URI.create("http://127.0.0.1:1/"), "password", HttpClient.newHttpClient(),
                "jcr:7", new PrintStream(printed, true, StandardCharsets.UTF_8));
    }

    @Test
    void theContainerSectionsAskAboutTheImageDockersMemoryTheContainerAndItsLogs() {
        JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();

        diagnose(docker, new ByteArrayOutputStream()).containerSections();

        assertThat(docker.asked).contains("docker image inspect jcr:7 --format {{.Architecture}}",
                "docker info --format {{.MemTotal}}",
                "docker ps -a --filter name=^inventory-jcr$ --format {{.Status}}",
                "docker exec inventory-jcr tail -40 /var/opt/jfrog/artifactory/log/access-service.log");
    }

    @Test
    void theNetworkSectionsPingJcrFromTheDemoNetworkAndFromInsideIt() {
        JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();

        diagnose(docker, new ByteArrayOutputStream()).networkSections();

        assertThat(docker.asked).contains("docker run --rm --network inventory-demo curlimages/curl:8.10.1 -s -m 5 "
                        + "-o /dev/null -w %{http_code}\n http://inventory-jcr:8082/artifactory/api/system/ping",
                "docker exec inventory-jcr curl -s -m 5 -o /dev/null -w %{http_code}\n "
                        + "http://localhost:8082/artifactory/api/system/ping",
                "docker exec inventory-jcr ls /var/opt/jfrog/artifactory/etc/artifactory");
    }

    @Test
    void theDockerApiSectionPrintsAProbeOfEachPath() {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();

        diagnose(new JcrContainersTest.Recorded(), printed).dockerApi();

        assertThat(printed.toString(StandardCharsets.UTF_8).lines().filter(line -> line.contains("no answer")))
                .hasSize(JcrDiagnose.DOCKER_API.size());
    }

    @Test
    void aSectionIsItsTitleThenWhatEachCommandSaid() {
        JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();
        docker.answers.put("one", new Commands.Result(0, "first\n"));
        docker.answers.put("two", new Commands.Result(0, "second\n"));
        ByteArrayOutputStream printed = new ByteArrayOutputStream();

        new JcrDiagnose(docker, URI.create("http://127.0.0.1:1/"), "password", HttpClient.newHttpClient(), "jcr:7",
                new PrintStream(printed, true, StandardCharsets.UTF_8)).section("title", List.of("one"),
                List.of("two"));

        assertThat(printed.toString(StandardCharsets.UTF_8)).isEqualTo("\n== title\nfirst\nsecond\n");
    }

    @Test
    void aProbeSaysTheStatusTheChallengeAndTheReplyWithItsTokenHidden() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"token\":\"secret\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer realm=x");
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JcrDiagnose diagnose = new JcrDiagnose(new JcrContainersTest.Recorded(), URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/"), "password", HttpClient.newHttpClient(), "jcr:7",
                    System.out);

            assertThat(diagnose.probe("v2/")).isEqualTo("401 Bearer realm=x {\"token\":\"(hidden)\"}");
        } finally {
            server.stop(0);
        }
    }
}
