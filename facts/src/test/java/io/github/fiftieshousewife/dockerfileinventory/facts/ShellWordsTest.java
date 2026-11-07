package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.FunctionParens;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Operator;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Word;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShellWordsTest {

    @Test
    void wordsAndOperatorsComeInOrderAndAQuotedWordIsMarked() {
        assertThat(ShellWords.of("a 'b' && c || d | e; f;; g\nh &x")).contains(List.of(
                new Word("a", false), new Word("b", true), Operator.AND_OR, new Word("c", false), Operator.AND_OR,
                new Word("d", false), Operator.PIPE, new Word("e", false), Operator.SEMICOLON, new Word("f", false),
                Operator.CASE_BREAK, new Word("g", false), Operator.NEWLINE, new Word("h", false),
                new Word("&x", false)));
    }

    @Test
    void parenthesesOpenASubshellOrAFunctionAndArraysBecomeOneQuotedAssignment() {
        assertThat(ShellWords.of("( a ) ; f() ; A=( x\n y )")).contains(List.of(
                Operator.OPEN, new Word("a", false), Operator.CLOSE, Operator.SEMICOLON, new FunctionParens("f"),
                Operator.SEMICOLON, new Word("A=x y", true)));
    }

    @Test
    void aCommentRunsToTheEndOfItsLineButAHashInsideAWordIsKept() {
        assertThat(ShellWords.of("a#b # note\nc # last")).contains(List.of(
                new Word("a#b", false), Operator.NEWLINE, new Word("c", false)));
    }

    @Test
    void aSingleLessThanIsKeptInTheWord() {
        assertThat(ShellWords.of("<in")).contains(List.of(new Word("<in", false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cat <<EOF", "echo a(b)", "A=( x y", "echo 'open"})
    void aHeredocAParenthesisGluedToAWordOrSomethingUnclosedIsUnreadable(String script) {
        assertThat(ShellWords.of(script)).isEmpty();
    }
}
