package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class FactsMainTest {

    @Test
    void aMissingSettingIsNamedInTheError() {
        assertThatIllegalStateException().isThrownBy(() -> FactsMain.required("FACTS_MAIN_TEST_UNSET"))
                .withMessage("FACTS_MAIN_TEST_UNSET is not set");
    }

    @Test
    void aSetSettingIsItsValue() {
        assertThat(FactsMain.required("PATH")).isEqualTo(System.getenv("PATH"));
    }
}
