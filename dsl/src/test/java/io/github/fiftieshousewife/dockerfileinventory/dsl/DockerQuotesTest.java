package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DockerQuotesTest {

    @Test
    void doubleQuotedKeepsEscapedPairsAndEscapesEveryOtherBackslashAndQuote() {
        assertThat(DockerQuotes.doubleQuoted("a b")).isEqualTo("\"a b\"");
        assertThat(DockerQuotes.doubleQuoted("\\$HOME \\\\ $PATH")).isEqualTo("\"\\$HOME \\\\ $PATH\"");
        assertThat(DockerQuotes.doubleQuoted("C:\\tools\\")).isEqualTo("\"C:\\\\tools\\\\\"");
        assertThat(DockerQuotes.doubleQuoted("say \"hi\"")).isEqualTo("\"say \\\"hi\\\"\"");
    }

    @Test
    void jsonArrayJoinsJsonStringsInBrackets() {
        assertThat(DockerQuotes.jsonArray(List.of("sh", "-c"))).isEqualTo("[\"sh\", \"-c\"]");
        assertThat(DockerQuotes.jsonArray(List.of())).isEqualTo("[]");
    }

    @Test
    void jsonStringEscapesBackslashesQuotesAndControlCharacters() {
        assertThat(DockerQuotes.jsonString("\\$ \"q\" a\nb\tc\rd\u0001"))
                .isEqualTo("\"\\\\$ \\\"q\\\" a\\nb\\tc\\rd\\u0001\"");
    }
}
