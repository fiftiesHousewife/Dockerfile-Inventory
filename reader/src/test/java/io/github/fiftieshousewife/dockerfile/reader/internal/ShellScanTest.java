package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class ShellScanTest {

    @Test
    void nextIsTheFollowingCharacterOrZeroAtTheEnd() {
        ShellScan scan = new ShellScan("ab");

        assertThat(scan.next()).isEqualTo('b');
        scan.at = 1;
        assertThat(scan.next()).isEqualTo((char) 0);
    }

    @Test
    void doubledConsumesARepeatedCharacterOnly() {
        ShellScan scan = new ShellScan("&&|");

        assertAll(
                () -> assertThat(scan.doubled('&')).isTrue(),
                () -> assertThat(scan.at).isEqualTo(1),
                () -> assertThat(scan.doubled('|')).isTrue(),
                () -> assertThat(scan.doubled('|')).isFalse(),
                () -> assertThat(scan.at).isEqualTo(2));
    }

    @Test
    void appendThroughTakesTheTextUpToTheClosingCharacter() {
        ShellScan scan = new ShellScan("`date` x");

        scan.appendThrough(5);

        assertAll(
                () -> assertThat(scan.word).hasToString("`date`"),
                () -> assertThat(scan.at).isEqualTo(5),
                () -> assertThat(scan.unreadable).isFalse());
    }

    @Test
    void appendThroughNothingClosedMakesTheScriptUnreadable() {
        ShellScan scan = new ShellScan("`date");

        scan.appendThrough(-1);

        assertThat(scan.unreadable).isTrue();
    }

    @Test
    void takeWordEmptiesTheWordAndForgetsItWasQuoted() {
        ShellScan scan = new ShellScan("");
        scan.word.append("abc");
        scan.quoted = true;

        assertAll(
                () -> assertThat(scan.takeWord()).isEqualTo("abc"),
                () -> assertThat(scan).satisfies(s -> {
                    assertThat(s.word).isEmpty();
                    assertThat(s.quoted).isFalse();
                }));
    }

    @Test
    void moreIsFalseAtTheEndOrOnceUnreadable() {
        ShellScan scan = new ShellScan("a");

        assertThat(scan.more()).isTrue();
        scan.unreadable = true;
        assertThat(scan.more()).isFalse();
    }
}
