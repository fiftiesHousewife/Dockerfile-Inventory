package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportMainTest {

    @Test
    void aRequiredSettingIsReturnedWhenSet() {
        assertThat(ReportMain.required(Map.of("OUT_DIR", "/out"), "OUT_DIR")).isEqualTo("/out");
    }

    @Test
    void aMissingOrBlankRequiredSettingStopsTheRunNamingIt() {
        assertAll(
                () -> assertThatThrownBy(() -> ReportMain.required(Map.of(), "OUT_DIR"))
                        .isInstanceOf(IllegalStateException.class).hasMessage("OUT_DIR is not set"),
                () -> assertThatThrownBy(() -> ReportMain.required(Map.of("FACTS_DIR", " "), "FACTS_DIR"))
                        .isInstanceOf(IllegalStateException.class).hasMessage("FACTS_DIR is not set"));
    }

    @Test
    void theCatalogueSettingsDefaultToTwelveMonthsAndFortyLayers() {
        assertThat(ReportMain.catalogueSettings(Map.of()))
                .isEqualTo(new CatalogueSettings(12, 40, FootprintRules.defaults(), LinkRule.DEFAULT));
    }

    @Test
    void theCatalogueSettingsAreReadFromTheEnvironment() {
        assertThat(ReportMain.catalogueSettings(Map.of("STALE_AFTER_MONTHS", "6", "LAYER_LIMIT", "60")))
                .isEqualTo(new CatalogueSettings(6, 60, FootprintRules.defaults(), LinkRule.DEFAULT));
    }

    @Test
    void aSettingThatIsNotAPositiveWholeNumberIsRefusedByName() {
        assertAll(
                () -> assertThatThrownBy(() -> ReportMain.catalogueSettings(Map.of("LAYER_LIMIT", "0")))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("LAYER_LIMIT must be a positive whole number, not 0"),
                () -> assertThatThrownBy(() -> ReportMain.catalogueSettings(Map.of("STALE_AFTER_MONTHS", "a year")))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("STALE_AFTER_MONTHS must be a positive whole number, not a year"));
    }

    @Test
    void anEstatesFootprintRulesAreReadAheadOfTheDefaults(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("footprints.csv"), "ocp/*,base-rhel*,full\n");

        assertThat(ReportMain.catalogueSettings(Map.of("FOOTPRINTS", file.toString())).footprints()
                .classify("registry.ci.openshift.org/ocp/5.0:base-rhel9")).isEqualTo("full");
    }

    @Test
    void theLinkThresholdAndMarginAreReadFromTheEnvironment() {
        assertThat(ReportMain.catalogueSettings(Map.of("LINK_THRESHOLD", "0.9", "LINK_MARGIN", "0.1")).linkRule())
                .isEqualTo(new LinkRule(0.9, 0.1));
    }

    @Test
    void aLinkSettingThatIsNotAShareBetweenZeroAndOneIsRefusedByName() {
        assertThatThrownBy(() -> ReportMain.catalogueSettings(Map.of("LINK_MARGIN", "5")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LINK_MARGIN must be a number from 0 to 1, not 5");
    }
}
