package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

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
                new ImageStage("build", new BaseImage("golang", "1.22", null), null, List.of()),
                new ImageStage("test", null, "build", List.of()),
                new ImageStage(null, new BaseImage("registry.access.redhat.com/ubi9/ubi-minimal", "9.4",
                        "sha256:abc"), null, List.of("build", "docker.io/library/busybox:1.36")));
    }
}
