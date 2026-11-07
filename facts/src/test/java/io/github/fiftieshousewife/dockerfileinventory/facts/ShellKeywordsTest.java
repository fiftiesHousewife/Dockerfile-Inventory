package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShellKeywordsTest {

    private static List<String> command(String... words) {
        return new ArrayList<>(List.of(words));
    }

    @Test
    void dropLeadingRemovesEveryKeywordBeforeTheCommandsName() {
        List<String> command = command("then", "if", "test", "fi");

        ShellKeywords.dropLeading(command);

        assertThat(command).containsExactly("test", "fi");
    }

    @Test
    void dropFunctionHeaderLeavesTheBodysFirstCommand() {
        List<String> braced = command("function", "setup", "{", "apk", "add", "git");
        List<String> bare = command("function", "setup");

        ShellKeywords.dropFunctionHeader(braced);
        ShellKeywords.dropFunctionHeader(bare);

        assertThat(braced).containsExactly("apk", "add", "git");
        assertThat(bare).isEmpty();
    }

    @Test
    void dropFunctionHeaderLeavesAnyOtherCommandAlone() {
        List<String> command = command("function");

        ShellKeywords.dropFunctionHeader(command);

        assertThat(command).containsExactly("function");
    }

    @Test
    void aForLoopsVariableTakesItsListExpanded() {
        assertThat(ShellKeywords.forAssignment(List.of("for", "p", "in", "git", "$MORE"), Map.of("MORE", "curl")))
                .contains(Map.entry("p", "git curl"));
    }

    @Test
    void aForLoopWithoutInOrOverWordsOnlyTheBuildKnowsAssignsNothing() {
        assertThat(ShellKeywords.forAssignment(List.of("for", "p"), Map.of())).isEmpty();
        assertThat(ShellKeywords.forAssignment(List.of("for", "p", "do"), Map.of())).isEmpty();
        assertThat(ShellKeywords.forAssignment(List.of("for", "f", "in", "/etc/*.conf"), Map.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*.conf", "a?", "$(ls)", "`ls`"})
    void aGlobOrCommandOutputIsWhatOnlyTheBuildKnows(String word) {
        assertThat(ShellKeywords.onlyTheBuildKnows(word)).isTrue();
    }

    @Test
    void aPlainWordIsKnown() {
        assertThat(ShellKeywords.onlyTheBuildKnows("git")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "select | false | true",
        "bash   | true  | true",
        "bash   | false | false",
        "sort   | true  | false",
    })
    void selectOrAShellPipedIntoIsUnreadable(String first, boolean pipedInto, boolean unreadable) {
        assertThat(ShellKeywords.unreadable(first, pipedInto)).isEqualTo(unreadable);
    }
}
