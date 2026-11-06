package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FaithfulBuilderTest {

    private static final BaseImage ALPINE = new BaseImage("alpine", "3.20", null);

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
}
