package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExpansionsTest {

    @Test
    void expandsBothFormsOfAKnownVariableAndKeepsAnUnknownOneAsWritten() {
        assertThat(Expansions.expanded("$A-${A}/$B $C", Map.of("A", "x", "B", "a$1\\b")))
                .isEqualTo("x-x/a$1\\b $C");
    }

    @Test
    void expandsAllOfABashArraysWordsLikeAVariable() {
        assertThat(Expansions.expanded("${A[@]} ${A[*]}", Map.of("A", "x y"))).isEqualTo("x y x y");
    }

    @Test
    void aWordWithADollarParenthesisOrABacktickHoldsACommandSubstitution() {
        assertAll(
                () -> assertThat(Expansions.holdsCommandSubstitution("$(uname -m)")).isTrue(),
                () -> assertThat(Expansions.holdsCommandSubstitution("`id -u`")).isTrue(),
                () -> assertThat(Expansions.holdsCommandSubstitution("${ARCH}")).isFalse());
    }
}
