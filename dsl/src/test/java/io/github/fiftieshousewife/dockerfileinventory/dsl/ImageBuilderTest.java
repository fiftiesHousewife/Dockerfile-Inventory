package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageBuilderTest {

    static final String DIGEST = "sha256:" + "b".repeat(64);
    static final BaseImage UBI_MINIMAL = new BaseImage("registry.example.com/ubi9/ubi-minimal", "9.4", DIGEST);
    static final BaseImage ALPINE = new BaseImage("alpine", "3.20", null);
    static final Policy POLICY = Policy.of(List.of(new Policy.Approved("ubi9/ubi-minimal", "9"),
            new Policy.Approved("ubi9/ubi", "9")));

    @Test
    void aValidImageBuildsAndDeclaredWritablePathsGetTheGroupZeroStepBeforeUser() {
        String text = DockerfileRenderer.render(valid().build(POLICY).dockerfile(), Target.DEFAULT);

        assertThat(text).containsSubsequence(
                "RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 java-21-openjdk-headless",
                "COPY --chown=0:0 app.jar /opt/app/",
                "RUN chgrp -R 0 /opt/app \\\n    && chmod -R g=u /opt/app",
                "USER 1001",
                "ENTRYPOINT [\"java\", \"-jar\", \"/opt/app/app.jar\"]");
    }

    @Test
    void s4TheFinalUserMustBeNumericAndNonRoot() {
        assertThat(breaches(ImageBuilder.finalStage(UBI_MINIMAL).entrypoint("app"))).containsExactly("S4");
        assertThat(breaches(ImageBuilder.finalStage(UBI_MINIMAL).user("app").entrypoint("app")))
                .containsExactly("S4");
        assertThat(breaches(ImageBuilder.finalStage(UBI_MINIMAL).user(0).entrypoint("app"))).containsExactly("S4");
    }

    @Test
    void s1AndR1TheFinalBaseMustBeApprovedAndPinnedByDigest() {
        assertThat(breaches(ImageBuilder.finalStage(new BaseImage("alpine", "3.20", DIGEST)).user(1001)
                .entrypoint("app"))).containsExactly("S1");
        assertThat(breaches(ImageBuilder.finalStage(new BaseImage("ubi9/ubi-minimal", "9.4", null)).user(1001)
                .entrypoint("app"))).containsExactly("R1");
    }

    @Test
    void r1ADigestFromABuildArgumentIsNotAPin() {
        assertThat(breachMessages(ImageBuilder.finalStage(
                new BaseImage("registry.example.com/ubi9/ubi-minimal", "9.4", "sha256:${BASE_SHA}")).user(1001)
                .entrypoint("app"))).containsExactly(
                        "R1: base registry.example.com/ubi9/ubi-minimal is pinned only by a build argument (BASE_SHA)");
    }

    @Test
    void h2AShellFormEntrypointRawOrWrittenAsTheDockerfileWroteItIsABreach() {
        assertThat(breaches(ImageBuilder.finalStage(UBI_MINIMAL).user(1001)
                .raw("ENTRYPOINT java -jar app.jar", "legacy start script"))).containsExactly("H2");
        assertThat(breaches(ImageBuilder.finalStage(UBI_MINIMAL).user(1001)
                .entrypoint(List.of("java -jar app.jar"), CommandForm.SHELL)
                .cmd(List.of("--serve"), CommandForm.SHELL))).containsExactly("H2");
    }

    @Test
    void h1AShellOrRawInstallThatDoesNotCleanIsABreach() {
        assertThat(breaches(valid().run("dnf install -y git"))).containsExactly("H1");
        assertThat(breaches(valid().raw("RUN apt-get install -y curl", "copied"))).containsExactly("H1");
        assertThat(breaches(valid().run("dnf install -y git", "dnf clean all"))).isEmpty();
        assertThat(breaches(valid().run("apk add --no-cache git"))).isEmpty();
    }

    @Test
    void everyBreachIsReportedTogether() {
        assertThat(breaches(ImageBuilder.finalStage(new BaseImage("alpine", "3.20", null))
                .raw("ENTRYPOINT ./start.sh", "legacy")))
                .containsExactlyInAnyOrder("S1", "R1", "S4", "H2");
    }

    @Test
    void aViolationListsEachBreachInItsMessage() {
        assertThatExceptionOfType(PolicyViolation.class)
                .isThrownBy(() -> ImageBuilder.finalStage(new BaseImage("alpine", "3.20", DIGEST)).build(POLICY))
                .withMessageContaining("S1").withMessageContaining("S4");
    }

    @Test
    void rawUsesAreListedInTheResult() {
        Built built = valid().raw("HEALTHCHECK NONE", "OpenShift probes instead").build(POLICY);

        assertThat(built.raw()).containsExactly(new Instruction.Raw("HEALTHCHECK NONE", "OpenShift probes instead"));
    }

    @Test
    void aBuildArgumentIsDeclaredInTheStageSoALabelCanUseIt() {
        String text = DockerfileRenderer.render(valid().arg("REVISION")
                .label("org.opencontainers.image.revision", "${REVISION}").build(POLICY).dockerfile(), Target.DEFAULT);

        assertThat(text).containsSubsequence("ARG REVISION\n",
                "LABEL org.opencontainers.image.revision=\"${REVISION}\"");
    }

    @Test
    void installOnAStageBuiltOnAnEarlierStageUsesTheEarlierStagesManager() {
        Dockerfile model = ImageBuilder.stage("deps", ALPINE).thenStageFrom(null, "deps").install("git").model();

        assertThat(DockerfileRenderer.render(model, Target.DEFAULT)).contains("RUN apk add --no-cache git");
    }

    @Test
    void modelReturnsWhatWasWrittenWithNoPolicyAdditionsOrChecks() {
        Dockerfile model = ImageBuilder.finalStage(ALPINE)
                .copy("app", "/opt/app/")
                .writablePaths("/opt/app")
                .user(1001)
                .model();

        assertThat(DockerfileRenderer.render(model, Target.DEFAULT)).isEqualTo("""
                FROM alpine:3.20
                COPY app /opt/app/
                USER 1001
                """);
    }

    @Test
    void checkListsTheBreachesBuildWouldThrow() {
        Policy policy = Policy.of(List.of(new Policy.Approved("ubi9/ubi-minimal", "9")));

        assertThat(ImageBuilder.finalStage(ALPINE).raw("ENTRYPOINT ./run.sh", "legacy").check(policy))
                .extracting(PolicyViolation.Breach::rule).containsExactlyInAnyOrder("S4", "S1", "R1", "H2");
    }

    @Test
    void theFaithfulMethodsRenderAsTheirInstructions() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("A", "1");
        env.put("B", "two words");
        Dockerfile model = ImageBuilder.create()
                .globalArg("BASE", "alpine:3.20")
                .thenStage("deps", ALPINE)
                .install(PackageManager.APK, List.of("--no-cache"), List.of("git"))
                .thenStageFrom("app", "deps")
                .env(env)
                .label(Map.of("team", "payments"))
                .copy(List.of("a", "b"), "/srv/", "app:app", "deps")
                .model();

        assertThat(DockerfileRenderer.render(model, Target.DEFAULT)).isEqualTo("""
                ARG BASE="alpine:3.20"

                FROM alpine:3.20 AS deps
                RUN apk add --no-cache git

                FROM deps AS app
                ENV A="1" \\
                    B="two words"
                LABEL team="payments"
                COPY --from=deps --chown=app:app a b /srv/
                """);
    }

    @Test
    void aCopyCanBeWrittenWithItsModeLinkAndForm() {
        assertThat(ImageBuilder.create().thenStage("app", ALPINE)
                .copy(List.of("run.sh"), "/bin/", null, null, "755", true, true).model().stages().getFirst()
                .instructions())
                .containsExactly(new Instruction.Copy(List.of("run.sh"), "/bin/", null, null, "755", true, true));
    }

    @Test
    void aFileCanBeWrittenFromLinesUnexpandedOrAsTheDockerfileWroteIt() {
        assertThat(ImageBuilder.create().thenStage("app", ALPINE).copyHeredoc("/etc/a.conf", List.of("a = 1"))
                .copyHeredoc("/etc/b", "END", false, List.of("$HOME")).model().stages().getFirst().instructions())
                .containsExactly(new Instruction.CopyHeredoc("/etc/a.conf", "EOF", true, List.of("a = 1")),
                        new Instruction.CopyHeredoc("/etc/b", "END", false, List.of("$HOME")));
    }

    @Test
    void addTakesAUrlOrAnArchiveAndRefusesALocalFileForCopy() {
        assertThat(ImageBuilder.create().thenStage("app", ALPINE).add("rootfs.tar.gz", "/").model().stages()
                .getFirst().instructions())
                .containsExactly(new Instruction.Add(List.of("rootfs.tar.gz"), "/", null, null, null, false, false,
                        false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImageBuilder.create().thenStage("app", ALPINE).add("app.jar", "/opt/"))
                .withMessageContaining("use copy");
    }

    @Test
    void anAddCanBeWrittenAsTheDockerfileWroteIt() {
        assertThat(ImageBuilder.create().thenStage("app", ALPINE)
                .add(List.of("app.jar"), "/opt/", "1001", "644", null, false, false, true).model().stages()
                .getFirst().instructions())
                .containsExactly(new Instruction.Add(List.of("app.jar"), "/opt/", "1001", "644", null, false, false,
                        true));
    }

    @Test
    void aRunOrAnInstallCanCarryFlags() {
        List<RunFlag> cache = List.of(RunFlag.Mount.parse("type=cache,target=/var/cache/apk"));

        assertThat(ImageBuilder.create().thenStage("app", ALPINE).run(cache, "make")
                .install(cache, PackageManager.APK, List.of(), List.of("git")).model().stages().getFirst()
                .instructions())
                .containsExactly(new Instruction.Run(new RunBody.Shell(List.of("make")), cache),
                        new Instruction.Run(new RunBody.Install(new PackageInstall(PackageManager.APK, List.of(),
                                List.of("git"))), cache));
    }

    @Test
    void aRunCanBeInExecFormOrAHeredoc() {
        assertThat(ImageBuilder.create().thenStage("app", ALPINE).runExec("/bin/opm", "serve")
                .runHeredoc(List.of(), "", "EOF", false, List.of("set -e", "make")).model().stages().getFirst()
                .instructions())
                .containsExactly(new Instruction.Run(new RunBody.Exec(List.of("/bin/opm", "serve"))),
                        new Instruction.Run(new RunBody.Heredoc("", "EOF", false, List.of("set -e", "make"))));
    }

    @Test
    void theInstructionJustAddedCanBeMadeAnOnbuildAndAHealthcheckGiven() {
        assertThat(ImageBuilder.create().thenStage("app", ALPINE).copy(".", "/app/").asOnbuild()
                .healthcheck(Map.of("retries", "3"), "curl", "-f", "http://localhost/").healthcheckNone().model()
                .stages().getFirst().instructions())
                .containsExactly(new Instruction.Onbuild(new Instruction.Copy(List.of("."), "/app/", null, null)),
                        new Instruction.Healthcheck(Map.of("retries", "3"), List.of("curl", "-f", "http://localhost/"),
                                CommandForm.EXEC),
                        Instruction.Healthcheck.NONE);
        assertThatIllegalStateException()
                .isThrownBy(() -> ImageBuilder.create().thenStage("app", ALPINE).asOnbuild());
    }

    @Test
    void portsCanBeGivenAsDockerWritesThemAndABadOneIsRefused() {
        assertThat(ImageBuilder.create().thenStage("app", ALPINE).expose("8080", "53/udp").expose(9090).model()
                .stages().getFirst().instructions())
                .containsExactly(new Instruction.Expose(List.of(Port.of(8080), new Port("53/udp"))),
                        new Instruction.Expose(List.of(Port.of(9090))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImageBuilder.create().thenStage("app", ALPINE).expose("80/http"));
    }

    @Test
    void aBuilderCanStartFromAModelAndAnArgCanHaveADefault() {
        Dockerfile model = ImageBuilder.create().globalArg("BASE", "alpine")
                .thenStage(null, ALPINE).arg("MODE", "prod").user(0).model();

        ImageBuilder again = ImageBuilder.of(model);

        assertThat(again.model()).isEqualTo(model);
        assertThat(again.check(Policy.of(List.of()))).extracting(PolicyViolation.Breach::rule).contains("S4", "S1");
        assertThat(DockerfileRenderer.render(model, Target.DEFAULT)).contains("ARG MODE=\"prod\"");
    }

    @Test
    void finishedMergesRunsButAddsNoPolicyStepsToAStageThatIsNotFinal() {
        ImageBuilder builder = ImageBuilder.stage("build", UBI_MINIMAL).run("a").run("b").copy("x", "/x")
                .writablePaths("/x").thenFrom(UBI_MINIMAL).user(1001);
        Stage build = builder.model().stages().getFirst();

        assertThat(builder.finished(Draft.of(build)).instructions()).containsExactly(
                new Instruction.Run(new RunBody.Shell(List.of("a", "b"))),
                new Instruction.Copy(List.of("x"), "/x", null, null));
    }

    @Test
    void rawListsOnlyTheRawInstructionsOfEveryStage() {
        ImageBuilder builder = ImageBuilder.stage("build", UBI_MINIMAL).raw("ONBUILD RUN x", "one")
                .thenFrom(UBI_MINIMAL).run("y").raw("HEALTHCHECK NONE", "two");

        assertThat(builder.raw()).extracting(Instruction.Raw::reason).containsExactly("one", "two");
    }

    private static ImageBuilder valid() {
        return ImageBuilder.finalStage(UBI_MINIMAL)
                .install("java-21-openjdk-headless")
                .copy("app.jar", "/opt/app/")
                .writablePaths("/opt/app")
                .user(1001)
                .entrypoint("java", "-jar", "/opt/app/app.jar");
    }

    private static List<String> breaches(ImageBuilder builder) {
        return breachMessages(builder).stream().map(message -> message.split(":", 2)[0]).toList();
    }

    private static List<String> breachMessages(ImageBuilder builder) {
        try {
            builder.build(POLICY);
            return List.of();
        } catch (PolicyViolation violation) {
            return violation.breaches().stream().map(b -> b.rule() + ": " + b.message()).toList();
        }
    }
}
