package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinalConfigTest {

    private static ImageConfig config(String dockerfile) {
        return FinalConfig.of(Parsed.file(dockerfile));
    }

    @Test
    void theWorkingDirectoryResolvesEachRelativeStepAndVolumesAccumulate() {
        ImageConfig config = config(
                "FROM ubi9\nWORKDIR /opt\nWORKDIR app\nVOLUME /data\nVOLUME [\"/cache\", \"/logs\"]\n");

        assertAll(
                () -> assertThat(config.workdir()).isEqualTo("/opt/app"),
                () -> assertThat(config.volumes()).containsExactly("/data", "/cache", "/logs"));
    }

    @Test
    void theLastHealthcheckShellAndStopSignalAreKeptAsWritten() {
        ImageConfig config = config("""
                FROM ubi9
                HEALTHCHECK NONE
                HEALTHCHECK --interval=30s CMD curl -f http://localhost/
                SHELL ["/bin/bash", "-c"]
                STOPSIGNAL SIGQUIT
                """);

        assertAll(
                () -> assertThat(config.healthcheck()).isEqualTo("--interval=30s CMD curl -f http://localhost/"),
                () -> assertThat(config.shell()).containsExactly("/bin/bash", "-c"),
                () -> assertThat(config.stopSignal()).isEqualTo("SIGQUIT"));
    }

    @Test
    void theEntrypointAndCommandKeepTheirFormAndWords() {
        ImageConfig config = config("FROM ubi9\nENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\nCMD --port 8080\n");

        assertAll(
                () -> assertThat(config.entrypoint())
                        .isEqualTo(new ImageConfig.Command(EntrypointForm.EXEC, List.of("java", "-jar", "app.jar"))),
                () -> assertThat(config.cmd())
                        .isEqualTo(new ImageConfig.Command(EntrypointForm.SHELL, List.of("--port 8080"))));
    }

    @Test
    void environmentAndLabelsKeepTheirValuesInOrderAndHideASecret() {
        ImageConfig config = config("""
                FROM ubi9
                ENV APP_HOME=/opt/app LEGACY_TOKEN=abc123
                ENV LANG en_US.UTF-8
                LABEL name="payments" version=1.2
                """);

        assertAll(
                () -> assertThat(config.environment()).containsExactly(Map.entry("APP_HOME", "/opt/app"),
                        Map.entry("LEGACY_TOKEN", "(hidden " + FinalConfig.hash("abc123") + ")"),
                        Map.entry("LANG", "en_US.UTF-8")),
                () -> assertThat(config.labels()).containsExactly(Map.entry("name", "payments"),
                        Map.entry("version", "1.2")));
    }

    @Test
    void layersAreTheFinalImagesRunCopyAndAddThroughTheStagesItIsBuiltOnByName() {
        assertThat(config("""
                FROM ubi9 AS base
                RUN dnf install -y git
                COPY a /a
                FROM golang AS build
                RUN go build
                FROM base
                ADD b.tar /b
                ENV X=1
                """).layers()).isEqualTo(3);
    }

    @Test
    void aRealDockerfilesConfigIsRead() throws IOException {
        ImageConfig config = FinalConfig.of(Parsed.file(Files.readString(
                Path.of("src/test/resources/fixtures/dockerfiles/python-311.Dockerfile"))));

        assertAll(
                () -> assertThat(config.environment()).containsEntry("PYTHON_VERSION", "3.11"),
                () -> assertThat(config.cmd())
                        .isEqualTo(new ImageConfig.Command(EntrypointForm.SHELL, List.of("$STI_SCRIPTS_PATH/usage"))),
                () -> assertThat(config.layers()).isEqualTo(5),
                () -> assertThat(config.labels()).containsEntry("io.k8s.description", "$DESCRIPTION"));
    }

    @Test
    void aWorkingDirectoryIsAbsoluteOrJoinedToTheOneBefore() {
        assertAll(
                () -> assertThat(FinalConfig.workdir(null, "app")).isEqualTo("/app"),
                () -> assertThat(FinalConfig.workdir("/opt/", "app")).isEqualTo("/opt/app"),
                () -> assertThat(FinalConfig.workdir("/opt", "/srv")).isEqualTo("/srv"));
    }

    @Test
    void aCommandThatIsNotValidJsonIsShellForm() {
        assertThat(FinalConfig.command("[\"grep\", \"\\d\"]"))
                .isEqualTo(new ImageConfig.Command(EntrypointForm.SHELL, List.of("[\"grep\", \"\\d\"]")));
    }

    @Test
    void legacyEnvHasOneNameAndTheRestOfTheLineAsItsValue() {
        assertThat(FinalConfig.pairs("JAVA_OPTS -Xmx1g -Xms1g"))
                .containsExactly(Map.entry("JAVA_OPTS", "-Xmx1g -Xms1g"));
    }
}
