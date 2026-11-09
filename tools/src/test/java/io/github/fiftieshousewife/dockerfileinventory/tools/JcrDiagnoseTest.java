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
