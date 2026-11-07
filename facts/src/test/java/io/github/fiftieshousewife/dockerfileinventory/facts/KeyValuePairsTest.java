package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.facts.KeyValuePairs.Pair;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyValuePairsTest {

    @Test
    void wordsSplitOnWhitespaceOutsideQuotesAndEachKeepsItsValueAsWritten() {
        assertThat(KeyValuePairs.of("a='x y' b=\"say \\\"hi there\\\" c=d\" e=1=2 f g= \"q.k\"=v"))
                .containsExactly(new Pair("a", "'x y'"), new Pair("b", "\"say \\\"hi there\\\" c=d\""),
                        new Pair("e", "1=2"), new Pair("f", null), new Pair("g", ""), new Pair("q.k", "v"));
    }

    @Test
    void keysAreUnquotedAndSorted() {
        assertThat(KeyValuePairs.keys(
                "vendor=\"Red Hat, Inc.\"   maintainer=\"Red Hat, Inc.\" \"build-date\"=\"2026\""))
                .containsExactly("build-date", "maintainer", "vendor");
    }

    @Test
    void anUnquotedValueIsItselfAndAQuotedOneLosesItsOuterQuotes() {
        assertThat(KeyValuePairs.unquoted("\"9\"")).isEqualTo("9");
        assertThat(KeyValuePairs.unquoted("'a b'")).isEqualTo("a b");
        assertThat(KeyValuePairs.unquoted("plain")).isEqualTo("plain");
    }

    @Test
    void wordsEndAtWhitespaceOutsideQuotesAndEscapes() {
        assertThat(KeyValuePairs.words("  a='x y'  b=c\\ d \"e f\"  "))
                .containsExactly("a='x y'", "b=c\\ d", "\"e f\"");
    }

    @Test
    void pairSplitsAtTheFirstEqualsOutsideQuotes() {
        assertThat(KeyValuePairs.pair("\"a=b\"=c=d")).isEqualTo(new Pair("a=b", "c=d"));
        assertThat(KeyValuePairs.pair("flag")).isEqualTo(new Pair("flag", null));
    }

    @Test
    void firstUnquotedEqualsSkipsQuotedAndEscapedOnes() {
        assertThat(KeyValuePairs.firstUnquotedEquals("'a=b'\\==c")).isEqualTo(7);
        assertThat(KeyValuePairs.firstUnquotedEquals("abc")).isEqualTo(-1);
    }

    @Test
    void plainRemovesQuotesAndKeepsEscapedCharactersAsThemselves() {
        assertThat(KeyValuePairs.plain("\"a b\"\\\"c'd\\e'")).isEqualTo("a b\"cd\\e");
    }

    @Test
    void addIfAnyMovesOnlyANonEmptyWordIntoTheList() {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();

        KeyValuePairs.addIfAny(words, word);
        word.append("a");
        KeyValuePairs.addIfAny(words, word);

        assertThat(words).containsExactly("a");
        assertThat(word).isEmpty();
    }

    @Test
    void aScanIsOutsideOnlyWhenNoQuoteIsOpenAndNothingIsEscaped() {
        KeyValuePairs.Scan scan = new KeyValuePairs.Scan();

        assertThat(scan.outside()).isTrue();
        scan.step('"');
        assertThat(scan.outside()).isFalse();
        assertThat(scan.literal('\'')).isTrue();
        scan.step('"');
        scan.step('\\');
        assertThat(scan.outside()).isFalse();
        assertThat(scan.literal('"')).isTrue();
    }
}
