package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.FunctionParens;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Operator;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Word;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellCommandsTest {

    private static Word word(String text) {
        return new Word(text, false);
    }

    private static List<List<String>> commands(ShellToken... tokens) {
        return ShellCommands.read(List.of(tokens)).orElseThrow().commands();
    }

    @Test
    void wordsAreReadAsCommandsAndLeadingAssignmentsAsAssignments() {
        ShellCommands read = ShellCommands.read(List.of(word("A=1"), word("make"), Operator.AND_OR, word("true")))
                .orElseThrow();

        assertThat(read).satisfies(r -> {
            assertThat(r.assignments()).containsExactly(Map.entry("A", "1"));
            assertThat(r.commands()).containsExactly(List.of("make"), List.of("true"));
        });
    }

    @Test
    void aClosingParenthesisOutsideASubshellOrCasePatternIsUnreadable() {
        assertThat(ShellCommands.read(List.of(word("a"), Operator.CLOSE))).isEmpty();
    }

    @Test
    void aNamedFunctionParenthesesInsideACommandIsUnreadable() {
        assertThat(ShellCommands.read(List.of(word("echo"), new FunctionParens("f")))).isEmpty();
    }

    @Test
    void emptyParenthesesInsideACommandAreAnEmptySubshell() {
        assertThat(commands(word("echo"), new FunctionParens(""), word("x"))).containsExactly(List.of("echo"),
                List.of("x"));
    }

    @Test
    void functionParenthesesStartingACommandOnlyMarkADefinition() {
        assertThat(commands(new FunctionParens("f"), word("make"))).containsExactly(List.of("make"));
    }

    @Test
    void aCasesPatternsAndAlternativesAreDroppedAndItsBranchesRead() {
        assertThat(commands(word("case"), word("$a"), word("in"), word("x"), Operator.PIPE, word("y"), Operator.CLOSE,
                word("make"), Operator.CASE_BREAK, word("*"), Operator.CLOSE, word("true"), Operator.CASE_BREAK,
                word("esac"), Operator.SEMICOLON, word("done")))
                .containsExactly(List.of("make"), List.of("true"));
    }

    @Test
    void aForLoopsListIsAssignedToItsVariableAndItsHeaderIsNoCommand() {
        ShellCommands read = ShellCommands.read(List.of(word("for"), word("p"), word("in"), word("a"), word("b"),
                Operator.SEMICOLON, word("do"), word("echo"), word("$p"), Operator.SEMICOLON, word("done")))
                .orElseThrow();

        assertThat(read).satisfies(r -> {
            assertThat(r.assignments()).containsExactly(Map.entry("p", "a b"));
            assertThat(r.commands()).containsExactly(List.of("echo", "$p"));
        });
    }

    @Test
    void aPipeIntoAShellIsUnreadable() {
        assertThat(ShellCommands.read(List.of(word("curl"), Operator.PIPE, word("sh")))).isEmpty();
    }

    @Test
    void aSubshellsCommandsAreTheScriptsOwn() {
        assertThat(commands(Operator.OPEN, word("cd"), word("/src"), Operator.CLOSE, Operator.AND_OR, word("make")))
                .containsExactly(List.of("cd", "/src"), List.of("make"));
    }
}
