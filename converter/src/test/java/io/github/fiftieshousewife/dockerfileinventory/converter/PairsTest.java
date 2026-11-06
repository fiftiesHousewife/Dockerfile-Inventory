package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PairsTest {

    @Test
    void aSingleQuotedValueRunsToTheNextSingleQuote() {
        assertThat(Pairs.parse("a='x y' b=c")).containsExactly(
                org.assertj.core.api.Assertions.entry("a", "x y"), org.assertj.core.api.Assertions.entry("b", "c"));
    }
}
