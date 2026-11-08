package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(scanned("\\a", ShellQuotes::escaped).word).hasToString("a");
        assertThat(scanned("\\$", ShellQuotes::escaped).word).hasToString(String.valueOf(ShellToken.LITERAL_DOLLAR));
    }

    @Test
    void aBackslashNewlineIsAContinuationAndAddsNothing() {
        assertThat(scanned("\\\nx", ShellQuotes::escaped)).satisfies(scan -> {
            assertThat(scan.word).isEmpty();
            assertThat(scan.at).isEqualTo(1);
        });
    }

    @Test
    void singleQuotesKeepTheirTextAsItIsWithDollarsMarkedLiteral() {
        assertThat(scanned("'a $b' c", ShellQuotes::singleQuoted)).satisfies(scan -> {
            assertThat(scan.word).hasToString("a " + ShellToken.LITERAL_DOLLAR + "b");
            assertThat(scan.quoted).isTrue();
            assertThat(scan.at).isEqualTo(5);
        });
    }

    @Test
    void anUnclosedQuoteMakesTheScriptUnreadable() {
        assertThat(scanned("'a", ShellQuotes::singleQuoted).unreadable).isTrue();
        assertThat(scanned("\"a", ShellQuotes::doubleQuoted).unreadable).isTrue();
    }

    @Test
    void doubleQuotesKeepDollarsAndSubstitutionsAndEndAtTheClosingQuote() {
        assertThat(scanned("\"$A `b` $(c)\" d", ShellQuotes::doubleQuoted)).satisfies(scan -> {
            assertThat(scan.word).hasToString("$A `b` $(c)");
            assertThat(scan.quoted).isTrue();
            assertThat(scan.at).isEqualTo(12);
        });
    }

    @Test
    void insideDoubleQuotesABackslashEscapesOnlyTheShellsFourCharacters() {
        assertThat(scanned("\"\\\" \\\\ \\a\"", ShellQuotes::doubleQuoted).word).hasToString("\" \\ \\a");
    }

    @Test
    void insideDoubleQuotesABackslashNewlineIsRemoved() {
        ShellScan scan = new ShellScan("\\\nx");

        ShellQuotes.escapedInDoubleQuotes(scan);

        assertThat(scan).satisfies(s -> {
            assertThat(s.word).isEmpty();
            assertThat(s.at).isEqualTo(1);
        });
    }

    @Test
    void insideDoubleQuotesABackslashEndingTheScriptIsUnreadable() {
        ShellScan scan = new ShellScan("\\");

        ShellQuotes.escapedInDoubleQuotes(scan);

        assertThat(scan.unreadable).isTrue();
    }

    @Test
    void aLiteralDollarIsMarkedAndAnyOtherCharacterIsItself() {
        assertThat(ShellQuotes.literal('$')).isEqualTo(ShellToken.LITERAL_DOLLAR);
        assertThat(ShellQuotes.literal('a')).isEqualTo('a');
    }
}
