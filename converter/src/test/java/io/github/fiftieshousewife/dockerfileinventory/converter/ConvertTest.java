package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageInstall;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConvertTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @Test
    void fromKeepsTagDigestAndStageNameAndAStageCanBuildOnAnEarlierOne() {
        Conversion conversion = Convert.of(Parsed.of("""
                ARG BASE=ubi9/ubi-minimal
                FROM registry.example.com/ubi9/ubi:9.4@DIGEST AS build
                FROM build AS test
                FROM ${BASE}:9.4
                """.replace("DIGEST", DIGEST)));

        assertThat(conversion.model().globalArgs()).containsExactly(new Instruction.Arg("BASE", "ubi9/ubi-minimal"));
        assertThat(conversion.model().stages()).extracting(Stage::name, Stage::base, Stage::fromStage).containsExactly(
                org.assertj.core.groups.Tuple.tuple("build",
                        new BaseImage("registry.example.com/ubi9/ubi", "9.4", DIGEST), null),
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

    @Test
    void aRunThatIsOnlyAnInstallAndItsCleanBecomesAnInstallWithItsFlagsAndAnyOtherRunAShell() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                RUN microdnf install -y git tar && microdnf clean all
                RUN apk add --no-cache git
                RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
                RUN dnf install -y git
                RUN DEBIAN_FRONTEND=noninteractive apt-get update && apt-get install -y curl \\
                    && rm -rf /var/lib/apt/lists/*
                RUN make \\
                    && make install
                RUN --mount=type=cache,target=/root/.m2 mvn package
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                install(PackageManager.MICRODNF, List.of("-y"), List.of("git", "tar")),
                install(PackageManager.APK, List.of("--no-cache"), List.of("git")),
                install(PackageManager.APT, List.of("-y"), List.of("curl")),
                shell("dnf install -y git"),
                shell("DEBIAN_FRONTEND=noninteractive apt-get update && apt-get install -y curl"
                        + " && rm -rf /var/lib/apt/lists/*"),
                shell("make && make install"),
                new Instruction.Raw("RUN --mount=type=cache,target=/root/.m2 mvn package", "RUN with flags"));
    }

    @Test
    void copyWithFromAndChownIsTypedAndEverythingTheModelDoesNotTypeStaysRawWithAReason() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                COPY --from=build --chown=1001:0 a b /dst/
                COPY app /opt/
                COPY --chmod=755 run.sh /usr/local/bin/
                COPY ["with space", "/opt/"]
                ADD app.tar.gz /opt/
                ENTRYPOINT java -jar app.jar
                CMD ./start.sh
                EXPOSE 8080/udp
                HEALTHCHECK NONE
                VOLUME /data
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Instruction.Copy(List.of("a", "b"), "/dst/", "1001:0", "build"),
                new Instruction.Copy(List.of("app"), "/opt/", null, null),
                new Instruction.Raw("COPY --chmod=755 run.sh /usr/local/bin/", "COPY with flag --chmod"),
                new Instruction.Raw("COPY [\"with space\", \"/opt/\"]", "COPY in JSON form"),
                new Instruction.Raw("ADD app.tar.gz /opt/", "ADD is not modelled"),
                new Instruction.Raw("ENTRYPOINT java -jar app.jar", "shell-form ENTRYPOINT"),
                new Instruction.Raw("CMD ./start.sh", "shell-form CMD"),
                new Instruction.Raw("EXPOSE 8080/udp", "EXPOSE with a protocol, range, variable or several ports"),
                new Instruction.Raw("HEALTHCHECK NONE", "HEALTHCHECK is not modelled"),
                new Instruction.Raw("VOLUME /data", "VOLUME is not modelled"));
    }

    private static Instruction install(PackageManager manager, List<String> flags, List<String> packages) {
        return new Instruction.Run(new RunBody.Install(new PackageInstall(manager, flags, packages)));
    }

    private static Instruction shell(String command) {
        return new Instruction.Run(new RunBody.Shell(List.of(command)));
    }
}
