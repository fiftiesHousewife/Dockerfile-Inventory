package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ShellQuotesTest {

    private static ShellScan scanned(String script, Consumer<ShellScan> step) {
        ShellScan scan = new ShellScan(script);
        step.accept(scan);
        return scan;
    }

    @Test
    void anEscapedCharacterIsItselfAndAnEscapedDollarIsMarkedLiteral() {
        assertAll(
                () -> assertThat(scanned("\\a", ShellQuotes::escaped).word).hasToString("a"),
                () -> assertThat(scanned("\\$", ShellQuotes::escaped).word)
                        .hasToString(String.valueOf(ShellToken.LITERAL_DOLLAR)));
    }

    @Test
    void aBackslashNewlineIsAContinuationAndAddsNothing() {
        assertAll(
                () -> assertThat(scanned("\\\nx", ShellQuotes::escaped).word).isEmpty(),
                () -> assertThat(scanned("\\\nx", ShellQuotes::escaped).at).isEqualTo(1));
    }

    @Test
    void singleQuotesKeepTheirTextAsItIsWithDollarsMarkedLiteral() {
        assertAll(
                () -> assertThat(scanned("'a $b' c", ShellQuotes::singleQuoted).word)
                        .hasToString("a " + ShellToken.LITERAL_DOLLAR + "b"),
                () -> assertThat(scanned("'a $b' c", ShellQuotes::singleQuoted).quoted).isTrue(),
                () -> assertThat(scanned("'a $b' c", ShellQuotes::singleQuoted).at).isEqualTo(5));
    }

    @Test
    void anUnclosedQuoteMakesTheScriptUnreadable() {
        assertAll(
                () -> assertThat(scanned("'a", ShellQuotes::singleQuoted).unreadable).isTrue(),
                () -> assertThat(scanned("\"a", ShellQuotes::doubleQuoted).unreadable).isTrue());
    }

    @Test
    void doubleQuotesKeepDollarsAndSubstitutionsAndEndAtTheClosingQuote() {
        assertAll(
                () -> assertThat(scanned("\"$A `b` $(c)\" d", ShellQuotes::doubleQuoted).word)
                        .hasToString("$A `b` $(c)"),
                () -> assertThat(scanned("\"$A `b` $(c)\" d", ShellQuotes::doubleQuoted).quoted).isTrue(),
                () -> assertThat(scanned("\"$A `b` $(c)\" d", ShellQuotes::doubleQuoted).at).isEqualTo(12));
    }

    @Test
    void insideDoubleQuotesABackslashEscapesOnlyTheShellsFourCharacters() {
        assertThat(scanned("\"\\\" \\\\ \\a\"", ShellQuotes::doubleQuoted).word).hasToString("\" \\ \\a");
    }

    @Test
    void insideDoubleQuotesABackslashNewlineIsRemoved() {
        ShellScan scan = new ShellScan("\\\nx");

        ShellQuotes.escapedInDoubleQuotes(scan);

        assertAll(
                () -> assertThat(scan.word).isEmpty(),
                () -> assertThat(scan.at).isEqualTo(1));
    }

    @Test
    void insideDoubleQuotesABackslashEndingTheScriptIsUnreadable() {
        ShellScan scan = new ShellScan("\\");

        ShellQuotes.escapedInDoubleQuotes(scan);

        assertThat(scan.unreadable).isTrue();
    }

    @Test
    void aLiteralDollarIsMarkedAndAnyOtherCharacterIsItself() {
        assertAll(
                () -> assertThat(ShellQuotes.literal('$')).isEqualTo(ShellToken.LITERAL_DOLLAR),
                () -> assertThat(ShellQuotes.literal('a')).isEqualTo('a'));
    }
}
