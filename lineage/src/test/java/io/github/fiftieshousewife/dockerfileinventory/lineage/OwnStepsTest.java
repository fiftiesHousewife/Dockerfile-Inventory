package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OwnStepsTest {

    private static final HistoryStep BASE = step("/bin/sh -c #(nop) ADD file:base in / ", null);
    private static final HistoryStep APP_FROM = step("/bin/sh -c #(nop) EXPOSE 8080", "FROM registry.example.com/base:1");
    private static final HistoryStep APP_RUN = step("/bin/sh -c make", null);
    private static final HistoryStep BUILDINFO = step("/bin/sh -c #(nop) COPY file:1 in /root/buildinfo/x.json ", null);

    @Test
    void theOwnStepsFollowTheParentsHistoryWithBuilderStepsDropped() {
        Resolved.Node parent = node(List.of(BASE, APP_FROM));
        Resolved.Node child = node(List.of(BASE, APP_FROM, BUILDINFO, APP_RUN));

        assertThat(OwnSteps.of(child, parent)).containsExactly(new Instruction("RUN", "make"));
    }

    @Test
    void withNoParentTheOwnStepsStartAtTheLastFromComment() {
        Resolved.Node image = node(List.of(BASE, APP_FROM, APP_RUN, BUILDINFO));

        assertThat(OwnSteps.of(image, null))
                .containsExactly(new Instruction("EXPOSE", "8080"), new Instruction("RUN", "make"));
    }

    @Test
    void withNoParentAndNoFromCommentTheWholeHistoryIsTheImagesOwn() {
        assertThat(OwnSteps.of(node(List.of(BASE, APP_RUN)), null))
                .containsExactly(new Instruction("ADD", "/"), new Instruction("RUN", "make"));
    }

    private static HistoryStep step(String createdBy, String comment) {
        return new HistoryStep(createdBy, comment, false, null);
    }

    private static Resolved.Node node(List<HistoryStep> history) {
        return new Resolved.Node("sha256:x", new ImageConfig(List.of("L1"), history, null, Map.of()));
    }
}
