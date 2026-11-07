package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openrewrite.docker.tree.Docker;

class ConvertPairsTest {

    @Test
    void anArgWithSeveralNamesGivesOneArgPerNameWithTheValueInTheModelsForm() {
        Docker.Arg arg = (Docker.Arg) Parsed.of("FROM ubi9\nARG A=\"x y\" B C='$5'\n")
                .getStages().getFirst().getInstructions().getFirst();

        assertThat(ConvertPairs.args(arg)).containsExactly(new Instruction.Arg("A", "x y"),
                new Instruction.Arg("B", null), new Instruction.Arg("C", "\\$5"));
    }

    @Test
    void afterKeywordDropsCommentLinesAndContinuationsAndTheKeyword() {
        assertThat(ConvertPairs.afterKeyword("ENV \\\n    # note\n    A=1 \\\n    B=2")).isEqualTo("A=1     B=2");
    }

    @Test
    void afterKeywordOfAnInstructionWithNoArgumentsIsEmpty() {
        assertThat(ConvertPairs.afterKeyword("ENV")).isEmpty();
    }

    @Test
    void pairsReadsKeyValueWordsOrTheLegacyFormThatSetsOneKey() {
        assertThat(ConvertPairs.pairs("a=1 b=\"two words\"")).containsExactly(entry("a", "1"),
                entry("b", "two words"));
        assertThat(ConvertPairs.pairs("maintainer \"A <a@example.com>\""))
                .containsExactly(entry("maintainer", "A <a@example.com>"));
    }

    @Test
    void envAndLabelAreReadFromTheirPrintedText() {
        assertThat(ConvertPairs.env("ENV A=1")).isEqualTo(new Instruction.Env(Map.of("A", "1")));
        assertThat(ConvertPairs.label("LABEL team x")).isEqualTo(new Instruction.Label(Map.of("team", "x")));
    }
}
