package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class HelpTest {

    @Test
    void aSettingSaysWhetherItIsRequiredOrItsDefault() {
        assertAll(
                () -> assertThat(Help.qualifier(Setting.required("OUT_DIR", "x"))).isEqualTo("required: "),
                () -> assertThat(Help.qualifier(Setting.optional("LINEAGE_DIR", "x"))).isEqualTo("optional: "),
                () -> assertThat(Help.qualifier(Setting.optional("GHE_CONCURRENCY", "8", "x")))
                        .isEqualTo("optional, default 8: "),
                () -> assertThat(Help.qualifier(Setting.requiredWith("A_RATE", "A_URL", "x")))
                        .isEqualTo("required with A_URL: "),
                () -> assertThat(Help.qualifier(Setting.requiredUnless("GHE_TOKEN", List.of("GHE_TOKEN_FILE",
                        "GHE_APP_ID"), "x"))).isEqualTo("required unless GHE_TOKEN_FILE or GHE_APP_ID is set: "));
    }

    @Test
    void eachCommandsSettingsAreListedAlignedOneALine() {
        assertThat(Help.command(Commands.FACTS)).isEqualTo("""
                docker-inventory facts: reads each Dockerfile into facts

                settings:
                  DISCOVERY_DIR  required: discovery's output folder
                  OUT_DIR        required: the output folder
                """);
    }
}
