package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImageBuilderTest {

    static final String DIGEST = "sha256:" + "b".repeat(64);
    static final BaseImage UBI_MINIMAL = new BaseImage("registry.example.com/ubi9/ubi-minimal", "9.4", DIGEST);
    static final Policy POLICY = Policy.of(List.of(new Policy.Approved("ubi9/ubi-minimal", "9"),
            new Policy.Approved("ubi9/ubi", "9")));

    private static ImageBuilder valid() {
        return ImageBuilder.finalStage(UBI_MINIMAL)
                .install("java-21-openjdk-headless")
                .copy("app.jar", "/opt/app/")
                .writablePaths("/opt/app")
                .user(1001)
                .entrypoint("java", "-jar", "/opt/app/app.jar");
    }

    @Test
    void aValidImageBuildsAndDeclaredWritablePathsGetTheGroupZeroStepBeforeUser() {
        String text = DockerfileRenderer.render(valid().build(POLICY).dockerfile(), Target.DEFAULT);

        assertThat(text).containsSubsequence(
                "RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 java-21-openjdk-headless",
                "COPY --chown=1001:0 app.jar /opt/app/",
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
    void h2AShellFormEntrypointIsOnlyPossibleThroughRawAndIsABreach() {
        assertThat(breaches(ImageBuilder.finalStage(UBI_MINIMAL).user(1001)
                .raw("ENTRYPOINT java -jar app.jar", "legacy start script"))).containsExactly("H2");
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
    void rawUsesAreListedInTheResult() {
        Built built = valid().raw("HEALTHCHECK NONE", "OpenShift probes instead").build(POLICY);

        assertThat(built.raw()).containsExactly(new Instruction.Raw("HEALTHCHECK NONE", "OpenShift probes instead"));
    }

    private static List<String> breaches(ImageBuilder builder) {
        try {
            builder.build(POLICY);
            return List.of();
        } catch (PolicyViolation violation) {
            return violation.breaches().stream().map(PolicyViolation.Breach::rule).toList();
        }
    }

    @Test
    void aViolationListsEachBreachInItsMessage() {
        assertThatExceptionOfType(PolicyViolation.class)
                .isThrownBy(() -> ImageBuilder.finalStage(new BaseImage("alpine", "3.20", DIGEST)).build(POLICY))
                .withMessageContaining("S1").withMessageContaining("S4");
    }
}
