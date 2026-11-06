package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConvertTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @Test
    void fromKeepsTagDigestAndStageNameAndAStageCanBuildOnAnEarlierOne() {
        Conversion conversion = Convert.of(Parsed.of("""
                ARG BASE=ubi9/ubi-minimal
                FROM registry.example.com/ubi9/ubi:9.4@%s AS build
                FROM build AS test
                FROM ${BASE}:9.4
                """.formatted(DIGEST)));

        assertThat(conversion.model().globalArgs()).containsExactly(new Instruction.Arg("BASE", "ubi9/ubi-minimal"));
        assertThat(conversion.model().stages()).extracting(Stage::name, Stage::base, Stage::fromStage).containsExactly(
                org.assertj.core.groups.Tuple.tuple("build", new BaseImage("registry.example.com/ubi9/ubi", "9.4", DIGEST),
                        null),
                org.assertj.core.groups.Tuple.tuple("test", null, "build"),
                org.assertj.core.groups.Tuple.tuple(null, new BaseImage("${BASE}", "9.4", null), null));
    }
}
