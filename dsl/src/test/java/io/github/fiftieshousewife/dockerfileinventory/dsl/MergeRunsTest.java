package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MergeRunsTest {

    private static String rendered(ImageBuilder builder) {
        return DockerfileRenderer.render(builder.build(ImageBuilderTest.POLICY).dockerfile(), Target.DEFAULT);
    }

    @Test
    void adjacentRunsInAStageIncludingAnInstallAndTheGroupZeroStepRenderAsOneRun() {
        String text = rendered(ImageBuilder.finalStage(ImageBuilderTest.UBI_MINIMAL)
                .install("git")
                .run("git config --system core.autocrlf false")
                .writablePaths("/opt/app")
                .user(1001)
                .entrypoint("app"));

        assertThat(text).contains("""
                RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 git \\
                    && microdnf clean all \\
                    && git config --system core.autocrlf false \\
                    && chgrp -R 0 /opt/app \\
                    && chmod -R g=u /opt/app
                USER 1001""");
    }

    @Test
    void runsSeparatedByAnotherInstructionOrInDifferentStagesStayApart() {
        String text = rendered(ImageBuilder.stage("build", ImageBuilderTest.UBI_MINIMAL)
                .run("make")
                .thenFrom(ImageBuilderTest.UBI_MINIMAL)
                .run("true")
                .copy("app", "/opt/app/")
                .run("false")
                .user(1001)
                .entrypoint("app"));

        assertThat(text).contains("RUN make\n\nFROM").contains("RUN true\nCOPY").contains("RUN false\nUSER");
    }

    @Test
    void aRunThatChangesShellStateIsNotJoinedToTheRunAfterIt() {
        String text = rendered(ImageBuilder.finalStage(ImageBuilderTest.UBI_MINIMAL)
                .run("cd /src && make")
                .run("make install")
                .run("export PATH=/opt/bin:$PATH")
                .run("tool --version")
                .user(1001)
                .entrypoint("app"));

        assertThat(text).contains("RUN cd /src && make\nRUN make install \\\n    && export PATH=/opt/bin:$PATH\n"
                + "RUN tool --version");
    }

    @Test
    void modelKeepsAdjacentRunsAsWritten() {
        Dockerfile model = ImageBuilder.finalStage(ImageBuilderTest.UBI_MINIMAL).run("a").run("b").model();

        assertThat(model.stages().getFirst().instructions()).hasSize(2);
    }
}
