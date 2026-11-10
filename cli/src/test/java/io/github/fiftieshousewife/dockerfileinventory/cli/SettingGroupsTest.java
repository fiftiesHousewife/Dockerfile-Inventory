package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class SettingGroupsTest {

    @Test
    void settingListsAreJoinedInOrder() {
        assertAll(
                () -> assertThat(SettingGroups.concat(List.of(List.of(Setting.required("A", "a")),
                        List.of(Setting.required("B", "b"))))).extracting(Setting::name).containsExactly("A", "B"),
                () -> assertThat(SettingGroups.concat(List.of())).isEmpty());
    }

    @Test
    void theGitHubSettingsStartWithItsRate() {
        assertThat(SettingGroups.GITHUB.getFirst()).isEqualTo(SettingGroups.GITHUB_RATE);
    }
}
