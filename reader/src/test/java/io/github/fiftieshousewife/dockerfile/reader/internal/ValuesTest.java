package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import org.junit.jupiter.api.Test;

class ValuesTest {

    @Test
    void unquoteRemovesDoubleQuotesAndUndoesTheirEscapes() {
        assertThat(Values.unquote("\"say \\\"hi\\\" C:\\\\x\"")).isEqualTo("say \"hi\" C:\\x");
    }

    @Test
    void unquoteRemovesSingleQuotesAndKeepsWhatIsInside() {
        assertThat(Values.unquote("'a \\\" b'")).isEqualTo("a \\\" b");
    }

    @Test
    void unquoteLeavesAnUnquotedOrHalfQuotedValueAlone() {
        assertThat(Values.unquote("\"open")).isEqualTo("\"open");
        assertThat(Values.unquote("\"")).isEqualTo("\"");
        assertThat(Values.unquote("plain")).isEqualTo("plain");
    }

    @Test
    void bracedWritesEveryBareVariableWithBraces() {
        assertThat(Values.braced("$UID:${GID}-$X1")).isEqualTo("${UID}:${GID}-${X1}");
    }

    @Test
    void textWritesTheParsersVariablesWithBraces() {
        Docker.From from = Parsed.of("FROM $REG/ubi9:${TAG}\n").getStages().getFirst().getFrom();

        assertThat(Values.text(from.getImageName()) + ":" + Values.text(from.getTag())).isEqualTo("${REG}/ubi9:${TAG}");
    }

    @Test
    void writtenPutsBackTheQuotesALiteralWasWrittenWith() {
        var volume = (Docker.Volume) Parsed.of(
                "FROM ubi9\nVOLUME \"/a b\" '/c' /d\n").getStages().getFirst().getInstructions().getFirst();

        assertThat(volume.getValues()).map(Values::written).containsExactly("\"/a b\"", "'/c'", "/d");
    }

    @Test
    void aFlagIsItsNameWithoutDashesAndItsValueAsWrittenOrEmpty() {
        var run = (Docker.Run) Parsed.of("FROM ubi9\nRUN --network=\"none\" --mount=type=cache,target=/c make\n")
                .getStages().getFirst().getInstructions().getFirst();

        assertThat(run.getFlags()).map(flag -> Values.flagName(flag) + " " + Values.flagValue(flag))
                .containsExactly("network \"none\"", "mount type=cache,target=/c");
    }

    @Test
    void flagsAreNoneWhenTheParserHasNone() {
        var run = (Docker.Run) Parsed.of("FROM ubi9\nRUN make\n").getStages().getFirst().getInstructions().getFirst();

        assertThat(Values.flags(run.getFlags())).isEmpty();
    }
}
