package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidatorMainTest {

    @TempDir
    Path out;

    @Test
    void withoutAProfileTheDefaultIsUsed() {
        ValidatorMain.run(Map.of("FACTS_DIR", "src/test/resources/fixtures", "DISCOVERY_DIR",
                "src/test/resources/fixtures", "OUT_DIR", out.toString()));

        assertThat(out.resolve("action-plan.json")).content().contains("\"openshift-4-restricted-v2\"");
    }

    @Test
    void aMissingSettingIsRefusedByName() {
        assertThatThrownBy(() -> ValidatorMain.run(Map.of("OUT_DIR", out.toString())))
                .hasMessage("FACTS_DIR must be set");
    }

    @Test
    void aBadProfileIsRefusedByNameBeforeAnythingIsRead() {
        assertThatThrownBy(() -> ValidatorMain.run(Map.of("OUT_DIR", out.toString(), "PROFILE", "openshift-5")))
                .hasMessageStartingWith("PROFILE openshift-5 is neither");
    }
}
