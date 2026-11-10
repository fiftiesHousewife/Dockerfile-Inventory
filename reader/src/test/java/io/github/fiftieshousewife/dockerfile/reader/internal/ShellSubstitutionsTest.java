package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShellSubstitutionsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "$(uname -m) x             | 10",
        "$(dirname $(pwd))         | 16",
        "$(echo \\))               | 9",
        "$(uname -m                | -1",
        "$(echo \"a)               | -1",
    })
    void endIsTheParenthesisClosingTheSubstitutionNestedOrEscapedOrMinusOne(String script, int end) {
        assertThat(ShellSubstitutions.end(script, 0)).isEqualTo(end);
    }

    @Test
    void endSkipsParenthesesInsideSingleOrDoubleQuotes() {
        assertThat(ShellSubstitutions.end("$(echo \")\" ')')", 0)).isEqualTo(14);
    }

    @Test
    void aDollarBeforeAParenthesisTakesTheWholeSubstitution() {
        ShellScan scan = new ShellScan("$(id -u) x");

        ShellSubstitutions.dollar(scan);

        assertAll(
                () -> assertThat(scan.word).hasToString("$(id -u)"),
                () -> assertThat(scan.at).isEqualTo(7));
    }

    @Test
    void anyOtherDollarIsItself() {
        ShellScan scan = new ShellScan("$A");

        ShellSubstitutions.dollar(scan);

        assertThat(scan.word).hasToString("$");
    }

    @Test
    void backticksTakeTheTextThroughTheClosingBacktickOrLeaveTheScriptUnreadable() {
        ShellScan closed = new ShellScan("`date` x");
        ShellScan open = new ShellScan("`date");

        ShellSubstitutions.backticks(closed);
        ShellSubstitutions.backticks(open);

        assertAll(
                () -> assertThat(closed.word).hasToString("`date`"),
                () -> assertThat(open.unreadable).isTrue());
    }
}
