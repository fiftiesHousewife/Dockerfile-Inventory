package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportMainTest {

    @Test
    void aRequiredSettingIsReturnedWhenSet() {
        assertThat(ReportMain.required(Map.of("OUT_DIR", "/out"), "OUT_DIR")).isEqualTo("/out");
    }

    @Test
    void aMissingOrBlankRequiredSettingStopsTheRunNamingIt() {
        assertThatThrownBy(() -> ReportMain.required(Map.of(), "OUT_DIR"))
                .isInstanceOf(IllegalStateException.class).hasMessage("OUT_DIR is not set");
        assertThatThrownBy(() -> ReportMain.required(Map.of("FACTS_DIR", " "), "FACTS_DIR"))
                .isInstanceOf(IllegalStateException.class).hasMessage("FACTS_DIR is not set");
    }
}
