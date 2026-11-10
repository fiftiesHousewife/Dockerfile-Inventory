package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlagsTest {

    @Test
    void theSettingsFileAndTheLoadAreTakenFromAnywhereAndTheRestKeptInOrder() {
        assertThat(Flags.of(List.of("survey", "--load", "gentle", "--try", "--settings", "s.yaml", "https://x/y")))
                .isEqualTo(new Flags(List.of("survey", "--try", "https://x/y"), Optional.of("s.yaml"),
                        Optional.of("gentle")));
    }

    @Test
    void aFlagWithoutItsValueIsAUsageError() {
        assertThatThrownBy(() -> Flags.of(List.of("survey", "--load"))).isInstanceOf(UsageError.class)
                .hasMessage("--load needs a value: gentle or normal");
    }
}
