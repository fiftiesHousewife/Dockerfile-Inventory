package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.facts.KeyValuePairs.Pair;
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
}
