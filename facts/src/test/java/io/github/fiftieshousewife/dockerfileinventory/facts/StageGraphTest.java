package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class StageGraphTest {

    @Test
    void eachStageHasItsNameItsBaseOrTheEarlierStageItBuildsOnAndWhatItCopiesFrom() {
        assertThat(StageGraph.of(Parsed.file("""
                ARG GO=1.22
                FROM golang:${GO} AS build
                RUN go build
                FROM build AS test
                RUN go test
                FROM registry.access.redhat.com/ubi9/ubi-minimal:9.4@sha256:abc
                COPY --from=build /out/app /usr/local/bin/
                COPY --from=docker.io/library/busybox:1.36 /bin/sh /bin/sh
                """))).containsExactly(
                new ImageStage("build", new BaseImage("golang", "1.22", null), null, List.of(), 1, 1, 1, 0, 1,
                        List.of(), List.of(), List.of()),
                new ImageStage("test", null, "build", List.of(), 1, 1, 1, 0, 1, List.of(), List.of(), List.of()),
                new ImageStage(null, new BaseImage("registry.access.redhat.com/ubi9/ubi-minimal", "9.4",
                        "sha256:abc"), null, List.of("build", "docker.io/library/busybox:1.36"), 2, 0, 0, 0, 2,
                        List.of(new CopyFact("COPY", List.of("/out/app"), "/usr/local/bin/", "stage", "build", null,
                                        null),
                                new CopyFact("COPY", List.of("/bin/sh"), "/bin/sh", "image",
                                        "docker.io/library/busybox:1.36", null, null)),
                        List.of(), List.of()));
    }

    @Test
    void aStageCountsItsCommandsAndUnreadRunsAndRecordsItsPermissionChanges() {
        ImageStage stage = StageGraph.of(Parsed.file("""
                FROM ubi9
                RUN mkdir -p /opt/app && chgrp -R 0 /opt/app && chmod -R g=u /opt/app
                RUN echo a ) b
                USER 1001
                """)).getFirst();

        assertAll(
                () -> assertThat(stage.instructions()).isEqualTo(3),
                () -> assertThat(stage.runs()).isEqualTo(2),
                () -> assertThat(stage.commands()).isEqualTo(3),
                () -> assertThat(stage.unreadRuns()).isEqualTo(1),
                () -> assertThat(stage.layers()).isEqualTo(2),
                () -> assertThat(stage.permissions()).containsExactly(
                        new PermissionChange("chgrp", "0", List.of("/opt/app"), true),
                        new PermissionChange("chmod", "g=u", List.of("/opt/app"), true)));
    }

    @Test
    void aStageListsItsUnusualStepsInOrder() {
        assertThat(StageGraph.of(Parsed.file("""
                FROM ubi9
                RUN --mount=type=cache,target=/c make
                ADD https://example.com/a.tgz /opt/
                HEALTHCHECK CMD true
                USER 1001
                """)).getFirst().unusual()).containsExactly("RUN --mount", "ADD of a URL", "HEALTHCHECK");
    }

    @Test
    void aRunsScriptIsItsTextAfterTheKeywordAndFlags() {
        assertAll(
                () -> assertThat(StageGraph.Scan.script("RUN --mount=type=cache,target=/c --network=none make"))
                        .isEqualTo("make"),
                () -> assertThat(StageGraph.Scan.script("RUN [\"make\"]")).isEqualTo("[\"make\"]"),
                () -> assertThat(StageGraph.Scan.script("RUN")).isEmpty());
    }
}
