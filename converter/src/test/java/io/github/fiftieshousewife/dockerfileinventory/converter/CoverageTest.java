package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CoverageTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void ofIsTypedInstructionsOverAllAfterFrom() {
        assertThat(Coverage.of(Convert.of(Parsed.of(
                "FROM ubi9\nUSER 1001\nRUN --device=/dev/fuse make\nCOPY --exclude=x . /s/\nWORKDIR /w\n"))))
                .isEqualTo(0.5);
    }

    @Test
    void aDockerfileWithNoInstructionsIsFullyCovered() {
        assertThat(Coverage.of(Convert.of(Parsed.of("FROM ubi9\n")))).isEqualTo(1.0);
    }

    @Test
    void jsonHoldsEachImagesCoverageAndHowOftenEachRawReasonOccurs() {
        Coverage coverage = new Coverage();
        coverage.add("b", Convert.of(Parsed.of(
                "FROM ubi9\nRUN --device=/dev/fuse make\nRUN --device=/dev/fuse make\n")));
        coverage.add("a", Convert.of(Parsed.of("FROM ubi9\nUSER 1001\n")));

        JsonNode json = JSON.readTree(coverage.json(JSON));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(json.path("images").propertyNames()).containsExactly("a", "b");
            softly.assertThat(json.path("images").path("b").asDouble()).isZero();
            softly.assertThat(json.path("rawReasons").path("RUN with flag --device").asInt()).isEqualTo(2);
        });
    }
}
