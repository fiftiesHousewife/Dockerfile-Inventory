package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundTripTest {

    @Test
    void theRealPython311DockerfileConvertsAndPasses() throws IOException {
        String original =
                Files.readString(Path.of("../facts/src/test/resources/fixtures/dockerfiles/python-311.Dockerfile"));

        RoundTrip result = RoundTrip.check(original, Convert.of(Parsed.of(original)).model());

        assertThat(result.difference()).isNull();
        assertThat(result.passed()).isTrue();
    }

    @Test
    void theRealSingleQuotedContinuedLabelsConvertAndPass() throws IOException {
        String original = Files.readString(Path.of("../facts/src/test/resources/fixtures/dockerfiles",
                "origin-atomic-registry-single-quoted-label.Dockerfile"));

        RoundTrip result = RoundTrip.check(original, Convert.of(Parsed.of(original)).model());

        assertThat(result.difference()).isNull();
    }

    @Test
    void aConversionThatDropsOrChangesAnInstructionFailsNamingTheStep() {
        String original = "FROM ubi9\nUSER 1001\nRUN make\n";
        Dockerfile converted = Convert.of(Parsed.of(original)).model();
        Stage stage = converted.stages().getFirst();
        Dockerfile dropped = new Dockerfile(List.of(), List.of(new Stage(null, stage.base(), null,
                List.of(stage.instructions().getFirst()))));
        Dockerfile changed = new Dockerfile(List.of(), List.of(new Stage(null, stage.base(), null,
                List.of(new Instruction.User("1002"), stage.instructions().get(1)))));

        assertThat(RoundTrip.check(original, dropped).difference()).contains("step 2").contains("RUN make");
        assertThat(RoundTrip.check(original, changed).difference()).contains("step 1").contains("1002");
    }

    @Test
    void flagAndPackageOrderInsideAnInstallDoNotCount() {
        String original = "FROM ubi9\nRUN microdnf install tar git -y && microdnf clean all\n";

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).passed()).isTrue();
    }

    @Test
    void quotingJsonSpacingAndAKeySetTwiceAreNotDifferencesDockerWouldSee() {
        String original = """
                FROM ubi9
                WORKDIR "$HOME"
                ENV A=1 A=2 B=3
                LABEL x=1 x=2
                CMD [ "run-thermostat-agent" ]
                """;

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }

    @Test
    void aBackslashFollowedByTrailingSpacesContinuesTheLineAsDockerReadsIt() {
        String original = "FROM ubi9\nLABEL Version=\"1.4\" \\ \n      Release=\"8\"\n";

        assertThat(Convert.of(Parsed.of(original)).model().stages().getFirst().instructions())
                .containsExactly(new Instruction.Label(java.util.Map.of("Version", "1.4", "Release", "8")));
        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }

    @Test
    void anOriginalUsingRunMountsAndHeredocsIsRenderedForABuilderThatHasThem() {
        String original = """
                FROM maven:3
                RUN --mount=type=cache,target=/root/.m2 mvn package
                RUN <<EOF
                echo built
                EOF
                """;

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }

    @Test
    void aBaseDigestFromABuildArgumentConvertsAndPasses() {
        String original = "ARG BASE_DOCKER_SHA\nFROM quay.io/prometheus/busybox@sha256:${BASE_DOCKER_SHA}\n"
                + "COPY app /bin/app\n";

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }

    @Test
    void aVariableWithOrWithoutBracesIsTheSameToDocker() {
        String original = "FROM ubi9\nARG UID GID\nUSER $UID:$GID\n";

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }

    @Test
    void aCopyHeredocIsKeptAsWrittenAndPasses() {
        String original = """
                FROM debian:bookworm-slim
                COPY <<EOF /etc/ocicrypt.conf
                {
                  "key-providers": { "attestation-agent": { "grpc": "localhost:50000" } }
                }
                EOF
                EXPOSE 50000
                """;

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }

    @Test
    void aBackslashKeptInsideDoubleQuotesIsTheSameValueAfterTheRoundTrip() {
        String original = "FROM ubi9\nLABEL vendor=\"Red Hat\\, Inc.\" name=x\n";

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }

    @Test
    void anEnvContinuedFromABareFirstLineWithCommentsBetweenItsValuesPasses() {
        String original = """
                FROM ubi9/dotnet-100-runtime
                ENV \\
                    # Microsoft's images set this to enable detecting when an app is running in a container.
                    DOTNET_RUNNING_IN_CONTAINER=true \\
                    # Configure ASP.NET Core to use the exposed port
                    ASPNETCORE_URLS=http://*:8080 \\
                    APP_UID=1001 \\
                    # Like Microsoft images, provide the .NET version.
                    DOTNET_VERSION=$DOTNET_VERSION

                USER 1001
                """;

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).difference()).isNull();
    }
}
