package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandsTest {

    @Test
    void theSurveyComesFirstThenEveryStageInTheOrderTheSurveyRunsThem() {
        assertThat(Commands.ALL).extracting(Command::name).containsExactly("survey", "discovery", "facts", "lineage",
                "catalogue", "report", "rules", "validator", "converter");
    }

    @Test
    void onlyTheSurveyTakesArguments() {
        assertThat(Commands.ALL).filteredOn(Command::arguments).extracting(Command::name).containsExactly("survey");
    }

    @Test
    void everyStageButTheSurveyNeedsAnOutputFolder() {
        assertThat(Commands.ALL.subList(1, Commands.ALL.size())).allMatch(command -> command.settings().stream()
                .anyMatch(setting -> setting.name().equals("OUT_DIR") && setting.required()));
    }

    @Test
    void noCommandListsASettingTwice() {
        assertThat(Commands.ALL).allMatch(command -> command.settings().stream().map(Setting::name).distinct()
                .count() == command.settings().size());
    }

    @Test
    void settingListsAreJoinedInOrder() {
        assertAll(
                () -> assertThat(Commands.concat(List.of(List.of(Setting.required("A", "a")),
                        List.of(Setting.required("B", "b"))))).extracting(Setting::name).containsExactly("A", "B"),
                () -> assertThat(Commands.concat(List.of())).isEmpty());
    }
}
