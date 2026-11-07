package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openrewrite.docker.tree.Docker;

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
}
