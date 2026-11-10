package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
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
    void aSurveyGivenNoOrganisationTakesTheSettingsUrl() {
        assertAll(
                () -> assertThat(Commands.surveyArguments(List.of("--try"), Map.of("GHE_API_URL", "https://g/a")))
                        .containsExactly("--try", "https://g/a"),
                () -> assertThat(Commands.surveyArguments(List.of("https://g/b"), Map.of("GHE_API_URL",
                        "https://g/a"))).containsExactly("https://g/b"),
                () -> assertThat(Commands.surveyArguments(List.of("--try"), Map.of()))
                        .containsExactly("--try"),
                () -> assertThat(Commands.surveyArguments(List.of(), Map.of("SURVEY_URLS", "https://g/a,https://g/b",
                        "GHE_API_URL", "https://g/c"))).containsExactly("https://g/a", "https://g/b"));
    }

    @Test
    void everyCommandThatCallsAServerRequiresItsRate() {
        assertAll(
                () -> assertThat(Commands.DISCOVERY.settings()).contains(Setting.required("GHE_MAX_REQUESTS_PER_SECOND",
                        SettingGroups.GITHUB_RATE.description())),
                () -> assertThat(Commands.LINEAGE.settings()).contains(SettingGroups.ARTIFACTORY_RATE),
                () -> assertThat(Commands.SURVEY.settings()).contains(SettingGroups.GITHUB_RATE,
                        Setting.requiredWith("ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "ARTIFACTORY_URL",
                                SettingGroups.ARTIFACTORY_RATE.description())));
    }
}
