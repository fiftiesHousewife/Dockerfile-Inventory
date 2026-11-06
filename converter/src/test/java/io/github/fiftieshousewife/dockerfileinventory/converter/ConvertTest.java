package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConvertTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @Test
    void fromKeepsTagDigestAndStageNameAndAStageCanBuildOnAnEarlierOne() {
        Conversion conversion = Convert.of(Parsed.of("""
                ARG BASE=ubi9/ubi-minimal
                FROM registry.example.com/ubi9/ubi:9.4@%s AS build
                FROM build AS test
                FROM ${BASE}:9.4
                """.formatted(DIGEST)));

        assertThat(conversion.model().globalArgs()).containsExactly(new Instruction.Arg("BASE", "ubi9/ubi-minimal"));
        assertThat(conversion.model().stages()).extracting(Stage::name, Stage::base, Stage::fromStage).containsExactly(
                org.assertj.core.groups.Tuple.tuple("build", new BaseImage("registry.example.com/ubi9/ubi", "9.4", DIGEST),
                        null),
                org.assertj.core.groups.Tuple.tuple("test", null, "build"),
                org.assertj.core.groups.Tuple.tuple(null, new BaseImage("${BASE}", "9.4", null), null));
    }

    @Test
    void simpleInstructionsBecomeTyped() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                ARG VERSION=1.0
                ARG REVISION
                ENV APP_HOME="/opt/app" MODE=prod
                ENV LEGACY value with spaces
                LABEL org.opencontainers.image.revision=$REVISION summary="say hi"
                WORKDIR /opt/app
                USER 1001:0
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "app.jar"]
                CMD ["serve"]
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Instruction.Arg("VERSION", "1.0"),
                new Instruction.Arg("REVISION", null),
                new Instruction.Env(java.util.Map.of("APP_HOME", "/opt/app", "MODE", "prod")),
                new Instruction.Env(java.util.Map.of("LEGACY", "value with spaces")),
                new Instruction.Label(java.util.Map.of("org.opencontainers.image.revision", "$REVISION",
                        "summary", "say hi")),
                new Instruction.Workdir("/opt/app"),
                new Instruction.User("1001:0"),
                new Instruction.Expose(8080),
                new Instruction.Entrypoint(List.of("java", "-jar", "app.jar")),
                new Instruction.Cmd(List.of("serve")));
    }
}
