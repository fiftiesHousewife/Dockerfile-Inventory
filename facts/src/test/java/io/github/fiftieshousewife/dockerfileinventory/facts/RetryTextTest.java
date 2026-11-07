package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RetryTextTest {

    private static Matcher argLine(String line) {
        Matcher arg = Pattern.compile("(?i)(\\s*ARG\\s+)?\\s*(\\S.*?)(\\s*\\\\)?\\s*").matcher(line);
        assertThat(arg.matches()).isTrue();
        return arg;
    }

    @Test
    void writesAFlagValuesColonDashDefaultAsADash() {
        assertThat(RetryText.flagDefaults("COPY --chown=${UID:-1001} a b")).isEqualTo("COPY --chown=${UID-1001} a b");
        assertThat(RetryText.flagDefaults("RUN echo ${A:-b}")).isEqualTo("RUN echo ${A:-b}");
    }

    @Test
    void givesEachPlainArgNameItsOwnValueAndAnEmptyDefaultQuotes() {
        assertThat(RetryText.of("ARG A B=1 C=\n")).isEqualTo("ARG A=$A B=1 C=\"\"\n");
    }

    @Test
    void aCommentHoldingAHeredocMarkerOpensNoHeredoc() {
        assertThat(RetryText.of("FROM a\n# cat <<EOF\nARG A B\n")).isEqualTo("FROM a\n\nARG A=$A B=$B\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "A    | A=$A",
        "A=   | A=\"\"",
        "A=1  | A=1",
    })
    void argWordGivesAPlainNameItsOwnReferenceAndAnEmptyDefaultQuotes(String word, String rewritten) {
        assertThat(RetryText.argWord(word)).isEqualTo(rewritten);
    }

    @Test
    void argNamesLeavesALineWithAQuotedValueAsItIs() {
        assertThat(RetryText.argNames(argLine("ARG A=\"x y\" B"))).isEqualTo("ARG A=\"x y\" B");
    }

    @Test
    void argNamesKeepsAContinuedLinesBackslash() {
        assertThat(RetryText.argNames(argLine("ARG A B=1 \\"))).isEqualTo("ARG A=$A B=1 \\");
    }

    @Test
    void theRewritesLeaveHeredocBodiesAndQuotedArgsAloneAndKeepAContinuedArgsBackslashes() {
        String dockerfile = """
                FROM ubi9
                ARG DESC="two words" OTHER
                ARG A \\
                    B
                RUN <<EOF
                ARG A B
                EOF
                """;

        assertThat(RetryText.of(dockerfile)).contains(
                "ARG DESC=\"two words\" OTHER\n", "ARG A=$A \\\n    B=$B\n", "RUN <<EOF\nARG A B\nEOF\n");
    }

    @Test
    void blankingCommentsKeepsLineNumbersParserDirectivesAndHeredocComments() {
        String text = """
                # syntax=docker/dockerfile:1
                FROM ubi9
                # it's here \\
                RUN make && \\
                # it's inside a continuation \\
                    make install
                RUN <<EOT
                # kept: part of the script
                echo hi
                EOT
                """;

        assertThat(RetryText.withoutComments(text)).isEqualTo("""
                # syntax=docker/dockerfile:1
                FROM ubi9

                RUN make && \\
                \\
                    make install
                RUN <<EOT
                # kept: part of the script
                echo hi
                EOT
                """);
    }

    @Test
    void aLoneBackslashLineOutsideAnInstructionIsBlanked() {
        assertThat(RetryText.withoutComments("COPY a b\n\\\nRUN x")).isEqualTo("COPY a b\n\nRUN x");
    }

    @Test
    void heredocBodiesMarksEachBodyLineAndItsClosingWordButACommentOpensNone() {
        assertThat(RetryText.heredocBodies(List.of("RUN <<EOF", "# body", "EOF", "# x <<END", "RUN y")))
                .containsExactly(false, true, true, false, false);
    }
}
