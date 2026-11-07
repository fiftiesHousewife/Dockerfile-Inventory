package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class BuildKitFeaturesTest {

    @Test
    void ofNamesEachFeatureTheFileUses() {
        assertThat(BuildKitFeatures.of(Parsed.file(
                "# syntax=docker/dockerfile:1\nFROM ubi9\nRUN --mount=type=cache,target=/c true\nRUN <<EOF\nx\nEOF\n")))
                .containsExactly("heredoc", "run-mount", "syntax-directive");
    }

    @Test
    void ofIsEmptyForAPlainDockerfile() {
        assertThat(BuildKitFeatures.of(Parsed.file("FROM ubi9\nRUN true\n"))).isEmpty();
    }

    @Test
    void mountsIsFalseForARunWithoutFlagsOrWithOtherFlags() {
        assertThat(BuildKitFeatures.mounts(null)).isFalse();
        assertThat(BuildKitFeatures.mounts(Parsed.first("FROM ubi9\nRUN --network=none true\n", Docker.Run.class)
                .getFlags())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "# syntax=docker/dockerfile:1\nFROM a",
        "# escape=`\n# SYNTAX = docker/dockerfile:1\nFROM a",
    })
    void aSyntaxDirectiveAmongTheLeadingDirectiveLinesCounts(String text) {
        assertThat(BuildKitFeatures.hasSyntaxDirective(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "# a comment\n# syntax=docker/dockerfile:1\nFROM a",
        "FROM a\n# syntax=docker/dockerfile:1",
    })
    void aSyntaxLineAfterAnyOtherLineIsAComment(String text) {
        assertThat(BuildKitFeatures.hasSyntaxDirective(text)).isFalse();
    }
}
