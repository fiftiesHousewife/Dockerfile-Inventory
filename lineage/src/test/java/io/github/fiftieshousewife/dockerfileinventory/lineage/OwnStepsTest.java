package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OwnStepsTest {

    /** Shared with facts, which must reduce the Dockerfile that built this image to the same steps. */
    private static final Path CONTRACT = Path.of("../testdata/contracts/python-311.steps.json");

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

    @Test
    void thePublicPython311HistoryGivesTheStepsOfTheDockerfileThatBuiltIt() throws IOException {
        JsonNode contract = new ObjectMapper().readTree(CONTRACT.toFile());
        List<Instruction> expected = new ArrayList<>();
        contract.path("steps").forEach(s -> expected.add(
                new Instruction(s.path("keyword").asString(), s.path("text").asString())));
        Resolved.Node image = new Resolved.Node("sha256:python",
                ImageConfig.parse(Fixtures.history("ubi9-python-311.json")).orElseThrow());

        assertThat(OwnSteps.of(image, null)).containsExactlyElementsOf(expected);
    }

    private static HistoryStep step(String createdBy, String comment) {
        return new HistoryStep(createdBy, comment, false, null);
    }

    private static Resolved.Node node(List<HistoryStep> history) {
        return new Resolved.Node("sha256:x", new ImageConfig(List.of("L1"), history, null, Map.of()));
    }
}
