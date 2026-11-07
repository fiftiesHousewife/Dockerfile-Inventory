package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConvertMetadataTest {

    @Test
    void aShellWhoseJsonIsNotValidStaysRawAsDockerCannotReadItEither() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nSHELL [\"/bin/bash\", \"\\d\"]\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).singleElement().isInstanceOf(Instruction.Raw.class);
    }

    @Test
    void aVolumeInJsonFormDecodesItsEscapesAndOneInShellFormKeepsItsWords() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nVOLUME [\"/with space\"]\nVOLUME /a /b\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(new Instruction.Volume(List.of("/with space"), true),
                new Instruction.Volume(List.of("/a", "/b"), false));
    }

    @Test
    void aShellFormVolumeKeepsEachWordAsWrittenQuotesIncluded() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nVOLUME \"/test_dir\" '/b' /c\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(new Instruction.Volume(List.of("\"/test_dir\"", "'/b'", "/c"), false));
    }
}
