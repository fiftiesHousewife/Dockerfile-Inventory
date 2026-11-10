package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Set;
import org.junit.jupiter.api.Test;

class NearMissesTest {

    private static final Set<String> SETTINGS = Set.of("GHE_TOKEN", "GHE_TOKEN_FILE", "OUT_DIR");

    @Test
    void aVariableStartingAsASettingWithinTwoEditsOfOneIsWarnedOf() {
        assertThat(NearMisses.warnings(Set.of("GHE_TOKN", "OUT_DIRS"), SETTINGS)).containsExactly(
                "GHE_TOKN is not a setting; did you mean GHE_TOKEN?",
                "OUT_DIRS is not a setting; did you mean OUT_DIR?");
    }

    @Test
    void theEnvironmentsOtherVariablesAndTheSettingsThemselvesAreLeftAlone() {
        assertThat(NearMisses.warnings(Set.of("PATH", "HOME", "GHE_TOKEN", "GHE_SOMETHING_ELSE"), SETTINGS)).isEmpty();
    }

    @Test
    void editsCountInsertionsDeletionsAndSubstitutions() {
        assertAll(
                () -> assertThat(NearMisses.edits("GHE_TOKEN", "GHE_TOKEN")).isZero(),
                () -> assertThat(NearMisses.edits("GHE_TOKN", "GHE_TOKEN")).isEqualTo(1),
                () -> assertThat(NearMisses.edits("OUT_DIT", "OUT_DIR")).isEqualTo(1),
                () -> assertThat(NearMisses.edits("", "ABC")).isEqualTo(3));
    }

    @Test
    void aNamesPrefixIsWhatComesBeforeItsFirstUnderscore() {
        assertAll(
                () -> assertThat(NearMisses.prefix("GHE_TOKEN_FILE")).isEqualTo("GHE"),
                () -> assertThat(NearMisses.prefix("PROFILE")).isEqualTo("PROFILE"));
    }
}
