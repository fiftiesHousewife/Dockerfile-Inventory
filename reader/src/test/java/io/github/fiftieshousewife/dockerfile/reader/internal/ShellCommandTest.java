package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfile.reader.internal.ShellToken.Word;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellCommandTest {

    private static ShellCommand command(Map<String, String> assignments, Word... words) {
        ShellCommand command = new ShellCommand();
        List.of(words).forEach(word -> command.add(word, assignments));
        return command;
    }

    private static Word word(String text) {
        return new Word(text, false);
    }

    @Test
    void aRedirectionAndItsSeparateTargetAreDroppedButAQuotedOneIsAWord() {
        ShellCommand command = command(new HashMap<>(), word("make"), word(">"), word("/dev/null"), word("2>&1"),
                new Word(">", true));

        assertThat(command.take()).containsExactly("make", ">");
    }

    @Test
    void leadingAssignmentsAreRecordedAndLaterOnesAreWords() {
        Map<String, String> assignments = new HashMap<>();

        ShellCommand command = command(assignments, word("A=1"), word("make"), word("X=2"));

        assertAll(
                () -> assertThat(assignments).containsExactly(Map.entry("A", "1")),
                () -> assertThat(command.take()).containsExactly("make", "X=2"));
    }

    @Test
    void assignExpandsEarlierAssignmentsAndRestoresLiteralDollars() {
        Map<String, String> assignments = new HashMap<>(Map.of("A", "1"));

        ShellCommand.assign("B=$A" + ShellToken.LITERAL_DOLLAR + "C", assignments);

        assertThat(assignments).containsEntry("B", "1$C");
    }

    @Test
    void dropKeywordsLeavesTheCommandInsideAnIfOrAFunctionHeader() {
        ShellCommand command = command(new HashMap<>(), word("then"), word("function"), word("f"), word("{"),
                word("make"));

        command.dropKeywords();

        assertThat(command.take()).containsExactly("make");
    }

    @Test
    void firstWordIsFalseForAnEmptyCommand() {
        ShellCommand command = new ShellCommand();

        assertAll(
                () -> assertThat(command.firstWordIs("case")).isFalse(),
                () -> assertThat(command.isEmpty()).isTrue());
    }

    @Test
    void takeEmptiesTheCommand() {
        ShellCommand command = command(new HashMap<>(), word("a"), word("b"));

        assertAll(
                () -> assertThat(command.take()).containsExactly("a", "b"),
                () -> assertThat(command.isEmpty()).isTrue());
    }
}
