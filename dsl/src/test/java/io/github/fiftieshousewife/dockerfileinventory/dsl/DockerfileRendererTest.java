package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DockerfileRendererTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    static final Dockerfile MODEL = new Dockerfile(List.of(new Instruction.Arg("VERSION", "1.0")), List.of(
            new Stage("build", new BaseImage("registry.example.com/ubi9/ubi", "9.4", DIGEST), null, List.of(
                    new Instruction.Run(new RunBody.Install(new PackageInstall(PackageManager.DNF, List.of("make")))),
                    new Instruction.Run(new RunBody.Shell(List.of("make build"))))),
            new Stage(null, new BaseImage("registry.example.com/ubi9/ubi-minimal", "9.4", DIGEST), null, List.of(
                    new Instruction.Label(ordered("org.opencontainers.image.revision", "${REVISION}",
                            "summary", "say \"hi\"")),
                    new Instruction.Env(ordered("APP_HOME", "/opt/app")),
                    new Instruction.Workdir("/opt/app"),
                    new Instruction.Copy(List.of("/src/out/app"), "/opt/app/", "1001:0", "build"),
                    new Instruction.Run(new RunBody.Shell(List.of("chgrp -R 0 /opt/app", "chmod -R g=u /opt/app"))),
                    new Instruction.Expose(8080),
                    new Instruction.User("1001"),
                    new Instruction.Entrypoint(List.of("/opt/app/app", "--config", "/etc/app \"main\".yaml")),
                    new Instruction.Cmd(List.of("serve")),
                    new Instruction.Raw("HEALTHCHECK NONE", "not modelled")))));

    @Test
    void everyInstructionRendersAsDockerReadsItWithOneCommandPerLineAndStagesSeparated() {
        assertThat(DockerfileRenderer.render(MODEL, Target.DEFAULT)).isEqualTo("""
                ARG VERSION="1.0"

                FROM registry.example.com/ubi9/ubi:9.4@DIGEST AS build
                RUN dnf install -y --nodocs --setopt=install_weak_deps=0 make \\
                    && dnf clean all
                RUN make build

                FROM registry.example.com/ubi9/ubi-minimal:9.4@DIGEST
                LABEL org.opencontainers.image.revision="${REVISION}" \\
                      summary="say \\"hi\\""
                ENV APP_HOME="/opt/app"
                WORKDIR /opt/app
                COPY --from=build --chown=1001:0 /src/out/app /opt/app/
                RUN chgrp -R 0 /opt/app \\
                    && chmod -R g=u /opt/app
                EXPOSE 8080
                USER 1001
                ENTRYPOINT ["/opt/app/app", "--config", "/etc/app \\"main\\".yaml"]
                CMD ["serve"]
                HEALTHCHECK NONE
                """.replace("DIGEST", DIGEST));
    }

    @Test
    void aHeredocOrRunMountIsRefusedUnlessTheTargetAllowsIt() {
        Dockerfile mount = single(new Instruction.Raw("RUN --mount=type=cache,target=/root/.m2 mvn package", "cache"));
        Dockerfile heredoc = single(new Instruction.Raw("RUN <<EOT\nmake\nEOT", "script"));

        assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.render(mount, Target.DEFAULT))
                .withMessageContaining("--mount");
        assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.render(heredoc, Target.DEFAULT))
                .withMessageContaining("heredoc");
        assertThat(DockerfileRenderer.render(mount, new Target(false, true))).contains("--mount");
        assertThat(DockerfileRenderer.render(heredoc, new Target(true, false))).contains("<<EOT");
    }

    @Test
    void anEscapedDollarStaysEscapedSoDockerReadsItLiterally() {
        Dockerfile label = single(new Instruction.Label(Map.of("INSTALL", "docker run \\$IMAGE")));

        assertThat(DockerfileRenderer.render(label, Target.DEFAULT)).contains("INSTALL=\"docker run \\$IMAGE\"");
    }

    @Test
    void anExecFormIsJsonSoEveryBackslashIsEscaped() {
        Dockerfile cmd = single(new Instruction.Cmd(List.of("sh", "-c", "echo \\$HOME")));

        assertThat(DockerfileRenderer.render(cmd, Target.DEFAULT)).contains("[\"sh\", \"-c\", \"echo \\\\$HOME\"]");
    }

    @Test
    void anExecFormsControlCharactersAreEscapedSoTheInstructionStaysOnOneLine() {
        Dockerfile cmd = single(new Instruction.Cmd(List.of("sh", "-c", "echo a\nb\tc\u0001")));

        assertThat(DockerfileRenderer.render(cmd, Target.DEFAULT))
                .contains("CMD [\"sh\", \"-c\", \"echo a\\nb\\tc\\u0001\"]\n");
    }

    @Test
    void escapedPairsAreKeptAndAnyOtherBackslashIsDoubled() {
        Dockerfile env = single(new Instruction.Env(new java.util.LinkedHashMap<>(Map.of("P", "pre\\\\$HOME"))));
        Dockerfile windows = single(new Instruction.Env(new java.util.LinkedHashMap<>(Map.of("W", "C:\\tools\\"))));

        assertThat(DockerfileRenderer.render(env, Target.DEFAULT)).contains("P=\"pre\\\\$HOME\"");
        assertThat(DockerfileRenderer.render(windows, Target.DEFAULT)).contains("W=\"C:\\\\tools\\\\\"");
    }

    @Test
    void anInstructionEndingInABackslashIsFollowedByABlankLineSoItIsNotReadAsAContinuation() {
        Dockerfile windows = new Dockerfile(List.of(), List.of(new Stage(null,
                new BaseImage("mcr.microsoft.com/windows/nanoserver", "ltsc2022", null), null, List.of(
                        new Instruction.Copy(List.of("C:\\\\Windows\\\\System32\\\\msvcp140.dll"),
                                "C:\\\\Windows\\\\System32\\\\", null, "builder"),
                        new Instruction.Env(Map.of("MONGO_VERSION", "8.3.13"))))));

        assertThat(DockerfileRenderer.render(windows, Target.DEFAULT))
                .contains("C:\\\\Windows\\\\System32\\\\\n\nENV MONGO_VERSION=\"8.3.13\"");
    }

    @Test
    void renderingIsStable() {
        assertThat(DockerfileRenderer.render(MODEL, Target.DEFAULT)).isEqualTo(DockerfileRenderer.render(MODEL,
                Target.DEFAULT));
    }

    private static Dockerfile single(Instruction instruction) {
        return new Dockerfile(List.of(), List.of(new Stage(null, new BaseImage("ubi9/ubi", "9.4", DIGEST), null,
                List.of(instruction))));
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
