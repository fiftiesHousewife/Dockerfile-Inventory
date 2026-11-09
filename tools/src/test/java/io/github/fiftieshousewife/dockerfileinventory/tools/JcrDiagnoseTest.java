package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JcrDiagnoseTest {

    @Test
    void aTokenInAReplyIsHiddenAndTheReplyCutToItsStart() {
        assertAll(
                () -> assertThat(JcrDiagnose.shown("{\"token\" : \"abc\", \"access_token\":\"def\"}\nrest"))
                        .isEqualTo("{\"token\":\"(hidden)\", \"access_token\":\"(hidden)\"} rest"),
                () -> assertThat(JcrDiagnose.shown("x".repeat(300))).hasSize(200));
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
}
