package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArgPairsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "$A   | true",
        "${A} | true",
        "$AB  | false",
        "1    | false",
    })
    void ownNameIsADefaultThatOnlyReferencesItsOwnName(String value, boolean own) {
        assertThat(ArgPairs.ownName(value, "A")).isEqualTo(own);
    }

    @Test
    void argPairsReadsEveryNameInOrderWithItsUnquotedValueOrNull() {
        Docker.Arg arg = Parsed.first("FROM ubi9\nARG A=1 B=\"two words\" C D=$D\n", Docker.Arg.class);

        assertThat(ArgPairs.of(arg)).containsExactly(
                Map.entry("A", "1"), Map.entry("B", "two words"), new SimpleEntry<>("C", null),
                new SimpleEntry<>("D", null));
    }
}
