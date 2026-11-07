package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseImagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "ubi9/ubi:9.4@sha256:abc         | ubi9/ubi                 | 9.4 | sha256:abc",
        "registry:5000/ubi9/ubi          | registry:5000/ubi9/ubi   | -   | -",
        "registry:5000/ubi9/ubi:9.4      | registry:5000/ubi9/ubi   | 9.4 | -",
        "ubi9/ubi@sha256:abc             | ubi9/ubi                 | -   | sha256:abc",
    })
    void splitSeparatesNameTagAndDigestNotMistakingARegistryPortForATag(String reference, String name, String tag,
            String digest) {
        assertThat(BaseImages.split(reference)).isEqualTo(new BaseImage(name, tag, digest));
    }

    @Test
    void baseRebuildsTheReferenceWithVariablesSubstitutedBeforeSplitting() {
        assertThat(BaseImages.base(Parsed.lastStage("FROM ${BASE}\n").getFrom(), Map.of("BASE", "ubi9/ubi:9.4")))
                .isEqualTo(new BaseImage("ubi9/ubi", "9.4", null));
    }

    @Test
    void finalBaseFollowsAStageBuiltFromAnEarlierStagesName() {
        assertThat(BaseImages.finalBase(Parsed.file("FROM ubi9/ubi:9.4 AS base\nFROM base\n"), Map.of()))
                .isEqualTo(new BaseImage("ubi9/ubi", "9.4", null));
    }

    @Test
    void finalBaseWithATagIsAnImageEvenWhenAStageHasItsName() {
        assertThat(BaseImages.finalBase(Parsed.file("FROM ubi9/ubi:9.4 AS base\nFROM base:1\n"), Map.of()))
                .isEqualTo(new BaseImage("base", "1", null));
    }
}
