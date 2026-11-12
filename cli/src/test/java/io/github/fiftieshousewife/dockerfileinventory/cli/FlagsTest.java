package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlagsTest {

    @Test
    void theSettingsFileAndTheLoadAreTakenFromAnywhereAndTheRestKeptInOrder() {
        assertThat(Flags.of(List.of("survey", "--load", "gentle", "--try", "--settings", "s.yaml", "https://x/y")))
                .isEqualTo(new Flags(List.of("survey", "--try", "https://x/y"), Optional.of("s.yaml"),
                        Optional.of("gentle"), Map.of()));
    }

    @Test
    void aFlagWithoutItsValueIsAUsageError() {
        assertThatThrownBy(() -> Flags.of(List.of("survey", "--load"))).isInstanceOf(UsageError.class)
                .hasMessage("--load needs a value: gentle or normal");
    }

    @Test
    void theSelectionTheCeilingAndYesBecomeTheirSettings() {
        Flags flags = Flags.of(List.of("survey", "--repository", "pay/api", "--repository-name-contains", "billing",
                "--repository", "pay/web", "--max-dockerfiles", "500", "--yes", "https://x/pay"));

        assertAll(
                () -> assertThat(flags.rest()).containsExactly("survey", "https://x/pay"),
                () -> assertThat(flags.settings()).isEmpty(),
                () -> assertThat(flags.environment()).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "REPOSITORIES", "pay/api,pay/web", "REPOSITORY_NAME_CONTAINS", "billing",
                        "MAX_DOCKERFILES", "500", "ASSUME_YES", "true")));
    }

    @Test
    void aCeilingThatIsNotAPositiveWholeNumberIsAUsageError() {
        assertThatThrownBy(() -> Flags.of(List.of("survey", "--max-dockerfiles", "lots")))
                .isInstanceOf(UsageError.class).hasMessage("--max-dockerfiles is a positive whole number, not lots");
    }

    @Test
    void theApiBecomesItsSetting() {
        assertThat(Flags.of(List.of("discovery", "--api", "rest")).environment())
                .containsExactlyEntriesOf(Map.of("GHE_API", "rest"));
    }
}
