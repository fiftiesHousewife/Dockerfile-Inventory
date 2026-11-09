package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineageMainTest {

    @Test
    void aRequiredSettingIsItsValue() {
        assertThat(LineageMain.required(Map.of("OUT_DIR", "/out"), "OUT_DIR")).isEqualTo("/out");
    }

    @Test
    void aMissingOrBlankRequiredSettingIsAnErrorNamingIt() {
        assertThatIllegalStateException().isThrownBy(() -> LineageMain.required(Map.of(), "OUT_DIR"))
                .withMessage("OUT_DIR is not set");
        assertThatIllegalStateException().isThrownBy(() -> LineageMain.required(Map.of("OUT_DIR", " "), "OUT_DIR"))
                .withMessage("OUT_DIR is not set");
    }

    @Test
    void withoutAnArtifactoryUrlOnlyTheAnalysisRunsOnWhatTheOutputDirectoryHolds(@TempDir Path out,
                                                                                @TempDir Path facts,
                                                                                @TempDir Path discovery) {
        LineageMain.run(Map.of("OUT_DIR", out.toString(), "FACTS_DIR", facts.toString(),
                "DISCOVERY_DIR", discovery.toString(), "ARTIFACTORY_URL", ""));

        assertThat(out.resolve("rates.json")).exists();
        assertThat(out.resolve("images.jsonl")).doesNotExist();
    }

    @Test
    void theLinkThresholdAndMarginAreReadFromTheEnvironment() {
        assertThat(LineageMain.linkRule(Map.of("LINK_THRESHOLD", "0.9", "LINK_MARGIN", "0.1")))
                .isEqualTo(new LinkRule(0.9, 0.1));
    }

    @Test
    void withoutTheLinkSettingsTheRuleIsTheDefault() {
        assertThat(LineageMain.linkRule(Map.of())).isEqualTo(LinkRule.DEFAULT);
    }

    @Test
    void aLinkSettingThatIsNotAShareBetweenZeroAndOneIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> LineageMain.linkRule(Map.of("LINK_MARGIN", "5")))
                .withMessage("LINK_MARGIN must be a number from 0 to 1, not 5");
    }
}
