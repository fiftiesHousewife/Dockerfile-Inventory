package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.model.Add;
import io.github.fiftieshousewife.dockerfile.model.Arg;
import io.github.fiftieshousewife.dockerfile.model.Cmd;
import io.github.fiftieshousewife.dockerfile.model.Copy;
import io.github.fiftieshousewife.dockerfile.model.Entrypoint;
import io.github.fiftieshousewife.dockerfile.model.Env;
import io.github.fiftieshousewife.dockerfile.model.Expose;
import io.github.fiftieshousewife.dockerfile.model.Label;
import io.github.fiftieshousewife.dockerfile.model.Maintainer;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Run;
import io.github.fiftieshousewife.dockerfile.model.Shell;
import io.github.fiftieshousewife.dockerfile.model.StopSignal;
import io.github.fiftieshousewife.dockerfile.model.User;
import io.github.fiftieshousewife.dockerfile.model.Volume;
import io.github.fiftieshousewife.dockerfile.model.Workdir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fiftieshousewife.dockerfile.model.BaseImage;
import io.github.fiftieshousewife.dockerfile.model.CommandForm;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.PackageInstall;
import io.github.fiftieshousewife.dockerfile.model.PackageManager;
import io.github.fiftieshousewife.dockerfile.model.Port;
import io.github.fiftieshousewife.dockerfile.model.RunBody;
import io.github.fiftieshousewife.dockerfile.model.RunFlag;
import io.github.fiftieshousewife.dockerfile.model.Stage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

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

        assertThat(conversion.model()).satisfies(
                model -> assertThat(model.globalArgs())
                        .containsExactly(new Arg("BASE", "ubi9/ubi-minimal")),
                model -> assertThat(model.stages()).extracting(Stage::name, Stage::base, Stage::fromStage)
                        .containsExactly(tuple("build", new BaseImage("registry.example.com/ubi9/ubi", "9.4", DIGEST),
                                        null),
                                tuple("test", null, "build"),
                                tuple(null, new BaseImage("${BASE}", "9.4", null), null)));
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
                new Arg("VERSION", "1.0"),
                new Arg("REVISION", null),
                new Env(Map.of("APP_HOME", "/opt/app", "MODE", "prod")),
                new Env(Map.of("LEGACY", "value with spaces")),
                new Label(Map.of("org.opencontainers.image.revision", "$REVISION",
                        "summary", "say hi")),
                new Workdir("/opt/app"),
                new User("1001:0"),
                new Expose(List.of(Port.of(8080))),
                new Entrypoint(List.of("java", "-jar", "app.jar")),
                new Cmd(List.of("serve")));
    }

    @Test
    void shellVolumeStopSignalAndMaintainerBecomeTypedKeepingTheVolumesForm() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                MAINTAINER Jane Doe <jane@example.com>
                SHELL ["/bin/bash", "-o", "pipefail", "-c"]
                VOLUME ["/data", "/var/log"]
                VOLUME /cache /tmp/x
                STOPSIGNAL SIGQUIT
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Maintainer("Jane Doe <jane@example.com>"),
                new Shell(List.of("/bin/bash", "-o", "pipefail", "-c")),
                new Volume(List.of("/data", "/var/log"), true),
                new Volume(List.of("/cache", "/tmp/x"), false),
                new StopSignal("SIGQUIT"));
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
                new Run(new RunBody.Shell(List.of("mvn package")),
                        List.of(RunFlag.Mount.parse("type=cache,target=/root/.m2"))));
    }

    @Test
    void copyWithFromAndChownIsTypedAndEverythingTheModelDoesNotTypeStaysRawWithAReason() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                COPY --from=build --chown=1001:0 a b /dst/
                COPY app /opt/
                COPY --exclude=*.md . /src/
                ADD app.tar.gz /opt/
                ENTRYPOINT java -jar app.jar
                CMD ./start.sh
                EXPOSE ${PORT:-8080}
                RUN --device=/dev/fuse make
                VOLUME /data
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Copy(List.of("a", "b"), "/dst/", "1001:0", "build"),
                new Copy(List.of("app"), "/opt/", null, null),
                new Raw("COPY --exclude=*.md . /src/", "COPY with flag --exclude"),
                new Add(List.of("app.tar.gz"), "/opt/", null, null, null, false, false, false),
                new Entrypoint(List.of("java -jar app.jar"), CommandForm.SHELL),
                new Cmd(List.of("./start.sh"), CommandForm.SHELL),
                new Raw("EXPOSE ${PORT:-8080}",
                        "EXPOSE with a port that is not a number, range or variable"),
                new Raw("RUN --device=/dev/fuse make", "RUN with flag --device"),
                new Volume(List.of("/data"), false));
    }

    @Test
    void continuationsInsideEnvAndLabelValuesAreRemovedAsDockerRemovesThem() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                ENV DESCRIPTION="one \\
                two"
                LABEL summary="three \\
                four" team=x
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Env(Map.of("DESCRIPTION", "one two")),
                new Label(Map.of("summary", "three four", "team", "x")));
    }

    @Test
    void labelsAfterATabWithCommentsInsideOrInTheLegacyFormAreReadAsDockerReadsThem() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                LABEL\topenshift.io/tags="database,mariadb" \\
                \tk8s.io/display-name="MariaDB 5.5"
                LABEL io.k8s.description="desc" \\
                # this tells where the scripts are (run, assemble) \\
                      io.openshift.tags="builder"
                LABEL maintainer "Shyam JVS <shyamjvs@google.com>"
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Label(Map.of("openshift.io/tags", "database,mariadb",
                        "k8s.io/display-name", "MariaDB 5.5")),
                new Label(Map.of("io.k8s.description", "desc", "io.openshift.tags", "builder")),
                new Label(Map.of("maintainer", "Shyam JVS <shyamjvs@google.com>")));
    }

    @Test
    void anExecFormsJsonEscapesAreDecodedAndAQuotedUserVariableIsWrittenWithBraces() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                USER "$GF_UID"
                ENTRYPOINT ["sh", "-c", "update-ca-trust extract && exec \\"$@\\"", "arg0"]
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(new User("${GF_UID}"),
                new Entrypoint(List.of("sh", "-c", "update-ca-trust extract && exec \"$@\"", "arg0")));
    }

    @Test
    void anExecFormThatIsNotValidJsonIsKeptRawAsDockerFallsBackToShellForm() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                CMD ["grep", "\\d+"]
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Raw("CMD [\"grep\", \"\\d+\"]", "exec form that is not valid JSON"));
    }

    @Test
    void anArgWithSeveralNamesBecomesOneArgPerNameGloballyAndInAStage() {
        Dockerfile model = Convert.of(Parsed.of("""
                ARG REG=quay.io TAG="9"
                FROM $REG/ubi9:$TAG
                # build arguments
                ARG UID=1001 GROUP
                USER $UID
                """)).model();

        assertThat(model).satisfies(
                m -> assertThat(m.globalArgs()).containsExactly(new Arg("REG", "quay.io"),
                        new Arg("TAG", "9")),
                m -> assertThat(m.stages().getFirst().instructions()).startsWith(new Arg("UID", "1001"),
                        new Arg("GROUP", null)));
    }

    @Test
    void anArgContinuedOverLinesDeclaresEachNameAndNoBackslash() {
        Dockerfile model = Convert.of(Parsed.of("""
                ARG GOVERSION="1.17" \\
                    GO_CHECKSUM="6bf8" \\
                    DEBIAN_FRONTEND
                FROM golang:$GOVERSION
                """)).model();

        assertThat(model.globalArgs()).containsExactly(new Arg("GOVERSION", "1.17"),
                new Arg("GO_CHECKSUM", "6bf8"), new Arg("DEBIAN_FRONTEND", null));
    }

    @Test
    void anEnvIsReadFromItsTextSoACommentedContinuationKeepsItsNames() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM registry.access.redhat.com/ubi9-minimal
                ENV \\
                    # Microsoft's images set this to enable detecting when an app is running in a container.
                    DOTNET_VERSION=$DOTNET_VERSION
                # ubi-minimal doesn't include timezones, restore them.
                RUN [ "$IS_CI" == "true" ] || ( echo x )
                """)).model().stages().getFirst().instructions();

        assertThat(instructions.getFirst()).isEqualTo(new Env(Map.of("DOTNET_VERSION", "$DOTNET_VERSION")));
    }

    @Test
    void aBaseKeepsItsTagAndDigestAndLeavesOutWhatIsNotWritten() {
        Docker.From tagged = Parsed.of("FROM ubi9:9.4@" + DIGEST + "\n").getStages().getFirst().getFrom();
        Docker.From bare = Parsed.of("FROM ubi9\n").getStages().getFirst().getFrom();

        assertThat(List.of(Convert.base(tagged, "ubi9"), Convert.base(bare, "ubi9")))
                .containsExactly(new BaseImage("ubi9", "9.4", DIGEST), new BaseImage("ubi9", null, null));
    }

    @Test
    void anInstructionTheModelDoesNotHaveIsRawNamingItsKeywordInCapitals() {
        assertThat(Convert.notModelled("stopsignal SIGTERM"))
                .isEqualTo(new Raw("stopsignal SIGTERM", "STOPSIGNAL is not modelled"));
    }

    @Test
    void aUserAndGroupAreUnquotedWithTheirVariablesBraced() {
        Docker.User withGroup = (Docker.User) first("FROM ubi9\nUSER \"$UID\":'0'\n");
        Docker.User alone = (Docker.User) first("FROM ubi9\nUSER 1001\n");

        assertThat(List.of(Convert.user(withGroup), Convert.user(alone)))
                .containsExactly(new User("${UID}:0"), new User("1001"));
    }

    @Test
    void anExposeKeepsEachPortAsWrittenOrIsNotTypedWhenOneIsNotAPort() {
        assertThat(List.of("EXPOSE 8080", "EXPOSE 80 53/udp 8000-8010/tcp $PORT", "EXPOSE 80 ${PORT:-8080}"))
                .map(line -> Convert.ports((Docker.Expose) first("FROM ubi9\n" + line + "\n")))
                .containsExactly(Optional.of(List.of(Port.of(8080))),
                        Optional.of(List.of(Port.of(80), new Port("53/udp"), new Port("8000-8010/tcp"),
                                new Port("$PORT"))),
                        Optional.empty());
    }

    private static Docker.Instruction first(String dockerfile) {
        return Parsed.of(dockerfile).getStages().getFirst().getInstructions().getFirst();
    }

    private static Instruction install(PackageManager manager, List<String> flags, List<String> packages) {
        return new Run(new RunBody.Install(new PackageInstall(manager, flags, packages)));
    }

    private static Instruction shell(String command) {
        return new Run(new RunBody.Shell(List.of(command)));
    }
}
