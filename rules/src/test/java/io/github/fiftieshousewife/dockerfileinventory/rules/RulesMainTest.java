package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RulesMainTest {

    @Test
    void anOptionalSettingThatIsUnsetOrBlankIsNoPath() {
        assertThat(RulesMain.optional(null)).isNull();
        assertThat(RulesMain.optional(" ")).isNull();
        assertThat(RulesMain.optional("out/lineage")).isEqualTo(Path.of("out/lineage"));
    }

    @Test
    void aRequiredSettingThatIsUnsetOrBlankStopsTheRunNamingIt() {
        assertThat(RulesMain.required("OUT_DIR", "out")).isEqualTo("out");
        assertThatThrownBy(() -> RulesMain.required("OUT_DIR", null)).hasMessage("OUT_DIR is not set");
        assertThatThrownBy(() -> RulesMain.required("OUT_DIR", "")).hasMessage("OUT_DIR is not set");
    }

    @Test
    void theSettingsGivenAreTheOnesReadAndAMissingOneIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> RulesMain.run(Map.of("OUT_DIR", "/o")))
                .withMessage("DISCOVERY_DIR is not set");
    }
}
