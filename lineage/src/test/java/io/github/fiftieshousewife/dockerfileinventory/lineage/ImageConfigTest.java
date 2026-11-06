package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageConfigTest {

    @Test
    void readsDiffIdsUserAndLabels() {
        assertThat(ImageConfig.parse(Fixtures.artifactory("config.json"))).hasValueSatisfying(config -> {
            assertThat(config.diffIds()).containsExactly("sha256:" + "3".repeat(64), "sha256:" + "4".repeat(64));
            assertThat(config.user()).isEqualTo("1001");
            assertThat(config.labels()).isEqualTo(Map.of("name", "team/app"));
        });
    }

    @Test
    void aMissingUserAndLabelsGiveNone() {
        assertThat(ImageConfig.parse(Fixtures.artifactory("config-bare.json"))).hasValueSatisfying(config -> {
            assertThat(config.user()).isNull();
            assertThat(config.labels()).isEmpty();
        });
    }

    @Test
    void keepsHistoryStepsInOrderWithEmptyLayerFalseWhenAbsent() {
        assertThat(ImageConfig.parse(Fixtures.artifactory("config-history.json")).orElseThrow().history())
                .extracting(HistoryStep::createdBy, HistoryStep::comment, HistoryStep::emptyLayer)
                .containsExactly(
                        tuple("/bin/sh -c #(nop) ADD file:base in / ", null, false),
                        tuple("/bin/sh -c #(nop) LABEL name=base", null, true),
                        tuple("/bin/sh -c microdnf install -y git", "FROM base:1", false),
                        tuple("/bin/sh -c #(nop) USER 1001", null, true));
    }

    @Test
    void saysWhetherTheStepsThatMadeLayersMatchTheLayers() {
        assertThat(ImageConfig.parse(Fixtures.artifactory("config-history.json")).orElseThrow().historyMatchesLayers())
                .isTrue();
        assertThat(ImageConfig.parse(Fixtures.artifactory("config-squashed.json")).orElseThrow().historyMatchesLayers())
                .isFalse();
    }

    @Test
    void textThatIsNotJsonOrHasNoRootfsIsUnreadable() {
        assertThat(ImageConfig.parse("not json")).isEmpty();
        assertThat(ImageConfig.parse("{\"config\": {}}")).isEmpty();
    }
}
