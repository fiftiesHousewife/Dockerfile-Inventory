package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PairKeysTest {

    @Test
    void givesTheKeysOfKeyValuePairsSorted() {
        assertThat(PairKeys.of("b=2 a=1   c=3")).containsExactly("a", "b", "c");
    }

    @Test
    void aDoubleQuotedKeyIsUnquotedAndADoubleQuotedValueWithSpacesStaysWhole() {
        assertThat(PairKeys.of("\"quoted.key\"=\"x y=z\" plain=1")).containsExactly("plain", "quoted.key");
    }

    @Test
    void aSingleQuotedValueStaysWholeAndASingleQuotedKeyIsUnquoted() {
        assertThat(PairKeys.of("INSTALL='docker run -v /:/host' 'odd key'=x")).containsExactly("INSTALL", "odd key");
    }

    @Test
    void aBackslashEscapesTheNextCharacterOutsideSingleQuotes() {
        assertThat(PairKeys.of("summary=\"say \\\"hi b=c\\\"\" k\\ ey=1")).containsExactly("k ey", "summary");
    }

    @Test
    void aQuoteInsideAWordIsNotAQuote() {
        assertThat(PairKeys.of("MSG=it's HOME=/x")).containsExactly("HOME", "MSG");
    }

    @Test
    void emptyTextHasNoKeys() {
        assertThat(PairKeys.of("   ")).isEmpty();
    }
}
