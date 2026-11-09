package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FactsMainTest {

    @Test
    void aMissingSettingIsNamedInTheError() {
        assertThatIllegalStateException().isThrownBy(() -> FactsMain.required(Map.of(), "FACTS_MAIN_TEST_UNSET"))
                .withMessage("FACTS_MAIN_TEST_UNSET is not set");
    }

    @Test
    void aSetSettingIsItsValue() {
        assertThat(FactsMain.required(Map.of("PATH", "/bin"), "PATH")).isEqualTo("/bin");
    }

    @Test
    void theSettingsGivenAreTheOnesReadAndAMissingOneIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> FactsMain.run(Map.of("DISCOVERY_DIR", "/d")))
                .withMessage("OUT_DIR is not set");
    }
}
