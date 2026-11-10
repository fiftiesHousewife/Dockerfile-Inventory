package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DockerfileRendererTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    static final Dockerfile MODEL = new Dockerfile(List.of(new Arg("VERSION", "1.0")), List.of(
            new Stage("build", new BaseImage("registry.example.com/ubi9/ubi", "9.4", DIGEST), null, List.of(
                    new Run(new RunBody.Install(new PackageInstall(PackageManager.DNF, List.of("make")))),
                    new Run(new RunBody.Shell(List.of("make build"))))),
            new Stage(null, new BaseImage("registry.example.com/ubi9/ubi-minimal", "9.4", DIGEST), null, List.of(
                    new Label(ordered("org.opencontainers.image.revision", "${REVISION}",
                            "summary", "say \"hi\"")),
                    new Env(ordered("APP_HOME", "/opt/app")),
                    new Workdir("/opt/app"),
                    new Copy(List.of("/src/out/app"), "/opt/app/", "1001:0", "build"),
                    new Run(new RunBody.Shell(List.of("chgrp -R 0 /opt/app", "chmod -R g=u /opt/app"))),
                    new Expose(List.of(Port.of(8080))),
                    new User("1001"),
                    new Entrypoint(List.of("/opt/app/app", "--config", "/etc/app \"main\".yaml")),
                    new Cmd(List.of("serve")),
                    new Raw("HEALTHCHECK NONE", "not modelled")))));

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
    void shellVolumeStopSignalAndMaintainerRenderAsDockerWritesThem() {
        Dockerfile file = new Dockerfile(List.of(), List.of(new Stage(null, new BaseImage("ubi9", "9.4", null), null,
                List.of(
                new Maintainer("Jane Doe <jane@example.com>"),
                new Shell(List.of("/bin/bash", "-o", "pipefail", "-c")),
                new Volume(List.of("/data", "/var/log"), true),
                new Volume(List.of("/cache"), false),
                new StopSignal("SIGQUIT")))));

        assertThat(DockerfileRenderer.render(file, Target.DEFAULT)).isEqualTo("""
                FROM ubi9:9.4
                MAINTAINER Jane Doe <jane@example.com>
                SHELL ["/bin/bash", "-o", "pipefail", "-c"]
                VOLUME ["/data", "/var/log"]
                VOLUME /cache
                STOPSIGNAL SIGQUIT
                """);
    }

    @Test
    void copyOptionsRenderAfterFromAndChownAndAJsonFormCopyKeepsItsForm() {
        Dockerfile file = single(
                new Copy(List.of("run.sh"), "/usr/local/bin/", "1001:0", "build", "755", true, false),
                new Copy(List.of("with space"), "/opt/", null, null, null, false, true));

        assertThat(DockerfileRenderer.render(file, Target.DEFAULT)).endsWith("""
                COPY --from=build --chown=1001:0 --chmod=755 --link run.sh /usr/local/bin/
                COPY ["with space", "/opt/"]
                """);
    }

    @Test
    void aCopyHeredocRendersItsLinesAndIsRefusedUnlessTheTargetAllowsHeredocs() {
        Dockerfile file = single(
                new CopyHeredoc("/etc/app.conf", "EOF", true, List.of("[engine]", "a = 1")),
                new CopyHeredoc("/etc/b.json", "END", false, List.of("{}")));

        assertAll(
                () -> assertThat(DockerfileRenderer.render(file, new Target(true, false))).endsWith("""
                        COPY <<'EOF' /etc/app.conf
                        [engine]
                        a = 1
                        EOF
                        COPY <<END /etc/b.json
                        {}
                        END
                        """),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.render(file,
                        Target.DEFAULT))
                        .withMessageContaining("heredoc"));
    }

    @Test
    void anAddRendersItsFlagsBeforeItsPathsInItsForm() {
        Dockerfile file = single(
                new Add(List.of("https://example.com/a.tgz"), "/opt/", "1001:0", "644", "sha256:abc", false,
                        true, false),
                new Add(List.of("https://github.com/o/r.git"), "/src", null, null, null, true, false,
                        true));

        assertThat(DockerfileRenderer.render(file, Target.DEFAULT)).endsWith("""
                ADD --chown=1001:0 --chmod=644 --checksum=sha256:abc --link https://example.com/a.tgz /opt/
                ADD --keep-git-dir ["https://github.com/o/r.git", "/src"]
                """);
    }

    @Test
    void aHealthcheckRendersItsOptionsInOrderThenItsCommandInItsFormAndOnbuildItsInstruction() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("interval", "30s");
        options.put("timeout", "3s");
        Dockerfile file = single(
                new Healthcheck(options, List.of("curl", "-f", "http://localhost/"), CommandForm.EXEC),
                new Healthcheck(Map.of(), List.of("curl -f http://localhost/ || exit 1"),
                        CommandForm.SHELL),
                Healthcheck.NONE,
                new Onbuild(new Copy(List.of("."), "/app/", null, null)));

        assertThat(DockerfileRenderer.render(file, Target.DEFAULT)).endsWith("""
                HEALTHCHECK --interval=30s --timeout=3s CMD ["curl", "-f", "http://localhost/"]
                HEALTHCHECK CMD curl -f http://localhost/ || exit 1
                HEALTHCHECK NONE
                ONBUILD COPY . /app/
                """);
    }

    @Test
    void aShellFormCmdOrEntrypointIsWrittenAsItsOneString() {
        Dockerfile file = single(new Entrypoint(List.of("exec app --serve"), CommandForm.SHELL),
                new Cmd(List.of("node server.js"), CommandForm.SHELL));

        assertThat(DockerfileRenderer.render(file, Target.DEFAULT))
                .endsWith("ENTRYPOINT exec app --serve\nCMD node server.js\n");
    }

    @Test
    void severalPortsRenderOnOneExposeLineAsWritten() {
        Dockerfile file = single(new Expose(List.of(Port.of(80), new Port("53/udp"), new Port("${PORT}"))));

        assertThat(DockerfileRenderer.render(file, Target.DEFAULT)).endsWith("EXPOSE 80 53/udp ${PORT}\n");
    }

    @Test
    void aRunsFlagsComeBeforeItsCommandsInTheirOrder() {
        Dockerfile file = single(new Run(new RunBody.Shell(List.of("mvn package")),
                List.of(RunFlag.Mount.parse("type=cache,target=/root/.m2"), new RunFlag.Network("none"))));

        assertAll(
                () -> assertThat(DockerfileRenderer.render(file, new Target(false, true)))
                        .endsWith("RUN --mount=type=cache,target=/root/.m2 --network=none mvn package\n"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.render(file,
                        Target.DEFAULT))
                        .withMessageContaining("--mount"));
    }

    @Test
    void anExecRunIsAJsonArrayAndAHeredocRunIsItsLinesRefusedUnlessTheTargetAllowsHeredocs() {
        Dockerfile file = single(new Run(new RunBody.Exec(List.of("/bin/opm", "serve", "/configs"))),
                new Run(new RunBody.Heredoc("", "EOF", false, List.of("set -e", "make"))));

        assertAll(
                () -> assertThat(DockerfileRenderer.render(file, new Target(true, false))).endsWith("""
                        RUN ["/bin/opm", "serve", "/configs"]
                        RUN <<EOF
                        set -e
                        make
                        EOF
                        """),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.render(file,
                        Target.DEFAULT))
                        .withMessageContaining("heredoc"));
    }

    @Test
    void aHeredocOrRunMountIsRefusedUnlessTheTargetAllowsIt() {
        Dockerfile mount = single(new Raw("RUN --mount=type=cache,target=/root/.m2 mvn package", "cache"));
        Dockerfile heredoc = single(new Raw("RUN <<EOT\nmake\nEOT", "script"));

        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.render(mount,
                        Target.DEFAULT))
                        .withMessageContaining("--mount"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.render(heredoc,
                        Target.DEFAULT))
                        .withMessageContaining("heredoc"),
                () -> assertThat(DockerfileRenderer.render(mount, new Target(false, true))).contains("--mount"),
                () -> assertThat(DockerfileRenderer.render(heredoc, new Target(true, false))).contains("<<EOT"));
    }

    @Test
    void anEscapedDollarStaysEscapedSoDockerReadsItLiterally() {
        Dockerfile label = single(new Label(Map.of("INSTALL", "docker run \\$IMAGE")));

        assertThat(DockerfileRenderer.render(label, Target.DEFAULT)).contains("INSTALL=\"docker run \\$IMAGE\"");
    }

    @Test
    void anExecFormIsJsonSoEveryBackslashIsEscaped() {
        Dockerfile cmd = single(new Cmd(List.of("sh", "-c", "echo \\$HOME")));

        assertThat(DockerfileRenderer.render(cmd, Target.DEFAULT)).contains("[\"sh\", \"-c\", \"echo \\\\$HOME\"]");
    }

    @Test
    void anExecFormsControlCharactersAreEscapedSoTheInstructionStaysOnOneLine() {
        Dockerfile cmd = single(new Cmd(List.of("sh", "-c", "echo a\nb\tc\u0001")));

        assertThat(DockerfileRenderer.render(cmd, Target.DEFAULT))
                .contains("CMD [\"sh\", \"-c\", \"echo a\\nb\\tc\\u0001\"]\n");
    }

    @Test
    void escapedPairsAreKeptAndAnyOtherBackslashIsDoubled() {
        Dockerfile env = single(new Env(Map.of("P", "pre\\\\$HOME")));
        Dockerfile windows = single(new Env(Map.of("W", "C:\\tools\\")));

        assertAll(
                () -> assertThat(DockerfileRenderer.render(env, Target.DEFAULT)).contains("P=\"pre\\\\$HOME\""),
                () -> assertThat(DockerfileRenderer.render(windows, Target.DEFAULT)).contains("W=\"C:\\\\tools\\\\\""));
    }

    @Test
    void anInstructionEndingInABackslashIsFollowedByABlankLineSoItIsNotReadAsAContinuation() {
        Dockerfile windows = new Dockerfile(List.of(), List.of(new Stage(null,
                new BaseImage("mcr.microsoft.com/windows/nanoserver", "ltsc2022", null), null, List.of(
                        new Copy(List.of("C:\\\\Windows\\\\System32\\\\msvcp140.dll"),
                                "C:\\\\Windows\\\\System32\\\\", null, "builder"),
                        new Env(Map.of("MONGO_VERSION", "8.3.13"))))));

        assertThat(DockerfileRenderer.render(windows, Target.DEFAULT))
                .contains("C:\\\\Windows\\\\System32\\\\\n\nENV MONGO_VERSION=\"8.3.13\"");
    }

    @Test
    void renderingIsStable() {
        assertThat(DockerfileRenderer.render(MODEL, Target.DEFAULT)).isEqualTo(DockerfileRenderer.render(MODEL,
                Target.DEFAULT));
    }

    @Test
    void fromNamesTheBaseWithTagAndDigestOrTheEarlierStageAndAnyAlias() {
        assertAll(
                () -> assertThat(DockerfileRenderer.from(new Stage("build", new BaseImage("alpine", "3.20", DIGEST),
                        null,
                        List.of()))).isEqualTo("FROM alpine:3.20@" + DIGEST + " AS build"),
                () -> assertThat(DockerfileRenderer.from(new Stage(null, null, "build", List.of())))
                        .isEqualTo("FROM build"));
    }

    @Test
    void referenceLeavesOutAMissingTagOrDigest() {
        assertAll(
                () -> assertThat(DockerfileRenderer.reference(new BaseImage("alpine", null, null))).isEqualTo("alpine"),
                () -> assertThat(DockerfileRenderer.reference(new BaseImage("alpine", null, DIGEST)))
                        .isEqualTo("alpine@" + DIGEST));
    }

    @Test
    void endedAddsABlankLineOnlyAfterATrailingBackslash() {
        assertAll(
                () -> assertThat(DockerfileRenderer.ended("COPY a C:\\")).isEqualTo("COPY a C:\\\n"),
                () -> assertThat(DockerfileRenderer.ended("USER 1001")).isEqualTo("USER 1001"));
    }

    @Test
    void supportedPassesALineTheTargetCanRead() {
        assertAll(
                () -> assertThat(DockerfileRenderer.supported("RUN make", Target.DEFAULT)).isEqualTo("RUN make"),
                () -> assertThat(DockerfileRenderer.supported("RUN --network=none --mount=type=cache make",
                        new Target(false, true)))
                        .contains("--mount"));
    }

    @Test
    void pairsAlignsEachFurtherPairUnderTheFirst() {
        assertThat(DockerfileRenderer.pairs("ENV", ordered("A", "1", "B", "2")))
                .isEqualTo("ENV A=\"1\" \\\n    B=\"2\"");
    }

    @Test
    void anArgHasItsDefaultDoubleQuotedOrNone() {
        assertThat(List.of(DockerfileRenderer.arg(new Arg("V", "1")), DockerfileRenderer.arg(new Arg("V", null))))
                .containsExactly("ARG V=\"1\"", "ARG V");
    }

    @Test
    void aRunWritesEachFlagBeforeItsBody() {
        Run run = new Run(new RunBody.Shell(List.of("make")), List.of(new RunFlag.Network("none")));

        assertThat(DockerfileRenderer.run(run)).isEqualTo("RUN --network=none make");
    }

    @Test
    void aVolumeKeepsItsJsonOrPlainForm() {
        assertThat(List.of(DockerfileRenderer.volume(new Volume(List.of("/a", "/b"), true)),
                DockerfileRenderer.volume(new Volume(List.of("/a", "/b"), false))))
                .containsExactly("VOLUME [\"/a\", \"/b\"]", "VOLUME /a /b");
    }

    @Test
    void stageRefusesAnInstructionTheTargetCannotRead() {
        Stage stage = new Stage(null, null, "build", List.of(new Raw("RUN <<EOT\nx\nEOT", "script")));

        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> DockerfileRenderer.stage(stage,
                        Target.DEFAULT)),
                () -> assertThat(DockerfileRenderer.stage(stage, new Target(true, false)))
                        .isEqualTo("FROM build\nRUN <<EOT\nx\nEOT"));
    }

    private static Dockerfile single(Instruction... instructions) {
        return new Dockerfile(List.of(), List.of(new Stage(null, new BaseImage("ubi9/ubi", "9.4", DIGEST), null,
                List.of(instructions))));
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
