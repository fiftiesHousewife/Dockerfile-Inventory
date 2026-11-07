package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;

class PairsTest {

    @Test
    void aSingleQuotedValueRunsToTheNextSingleQuote() {
        assertThat(Pairs.parse("a='x y' b=c")).containsExactly(entry("a", "x y"), entry("b", "c"));
    }

    @Test
    void aLiteralDollarIsStoredEscapedAndAVariableIsNot() {
        assertThat(Pairs.parse("a='run $IMAGE' b=\"run $IMAGE\" c=\"cost \\$5\"")).containsExactly(
                entry("a", "run \\$IMAGE"), entry("b", "run $IMAGE"), entry("c", "cost \\$5"));
    }

    @Test
    void insideDoubleQuotesABackslashBeforeAnythingButAQuoteBackslashOrDollarIsKept() {
        assertThat(Pairs.parse("W=\"C:\\tools\\bin\" Q=\"say \\\"hi\\\"\"")).containsExactly(
                entry("W", "C:\\\\tools\\\\bin"), entry("Q", "say \"hi\""));
    }

    @Test
    void aLiteralBackslashBeforeAVariableStaysDistinctFromALiteralDollar() {
        assertThat(Pairs.parse("P=\"pre\\\\$HOME\" D=\"cost \\$5\" S='a\\b'")).containsExactly(
                entry("P", "pre\\\\$HOME"), entry("D", "cost \\$5"), entry("S", "a\\\\b"));
    }

    @Test
    void aWordWithoutAnEqualsSignHasAnEmptyValue() {
        assertThat(Pairs.parse("NAME")).containsExactly(entry("NAME", ""));
    }

    @Test
    void modelFormUnescapesAnUnquotedBackslashAndKeepsATrailingOne() {
        assertThat(Pairs.modelForm("a\\ b\\")).isEqualTo("a b");
    }

    @Test
    void literalEscapesADollarOrABackslashAndDropsTheEndOfText() {
        assertThat(new String[] {Pairs.literal('$'), Pairs.literal('\\'), Pairs.literal('x'), Pairs.literal('\0')})
                .containsExactly("\\$", "\\\\", "x", "");
    }

    @Test
    void escapedInDoubleQuotesAppendsWhatTheEscapeMeansAndHowManyCharactersItTook() {
        StringBuilder value = new StringBuilder();

        int taken = Pairs.escapedInDoubleQuotes('"', value) + Pairs.escapedInDoubleQuotes('$', value)
                + Pairs.escapedInDoubleQuotes('\n', value) + Pairs.escapedInDoubleQuotes('t', value);

        assertThat(value.toString() + taken).isEqualTo("\"\\$\\\\3");
    }
}
