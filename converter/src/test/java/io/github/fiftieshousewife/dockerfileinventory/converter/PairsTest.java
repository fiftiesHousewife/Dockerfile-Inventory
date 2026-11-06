package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PairsTest {

    @Test
    void aSingleQuotedValueRunsToTheNextSingleQuote() {
        assertThat(Pairs.parse("a='x y' b=c")).containsExactly(
                org.assertj.core.api.Assertions.entry("a", "x y"), org.assertj.core.api.Assertions.entry("b", "c"));
    }

    @Test
    void aLiteralDollarIsStoredEscapedAndAVariableIsNot() {
        assertThat(Pairs.parse("a='run $IMAGE' b=\"run $IMAGE\" c=\"cost \\$5\"")).containsExactly(
                org.assertj.core.api.Assertions.entry("a", "run \\$IMAGE"),
                org.assertj.core.api.Assertions.entry("b", "run $IMAGE"),
                org.assertj.core.api.Assertions.entry("c", "cost \\$5"));
    }

    @Test
    void insideDoubleQuotesABackslashBeforeAnythingButAQuoteBackslashOrDollarIsKept() {
        assertThat(Pairs.parse("W=\"C:\\tools\\bin\" Q=\"say \\\"hi\\\"\"")).containsExactly(
                org.assertj.core.api.Assertions.entry("W", "C:\\\\tools\\\\bin"),
                org.assertj.core.api.Assertions.entry("Q", "say \"hi\""));
    }

    @Test
    void aLiteralBackslashBeforeAVariableStaysDistinctFromALiteralDollar() {
        assertThat(Pairs.parse("P=\"pre\\\\$HOME\" D=\"cost \\$5\" S='a\\b'")).containsExactly(
                org.assertj.core.api.Assertions.entry("P", "pre\\\\$HOME"),
                org.assertj.core.api.Assertions.entry("D", "cost \\$5"),
                org.assertj.core.api.Assertions.entry("S", "a\\\\b"));
    }
}
