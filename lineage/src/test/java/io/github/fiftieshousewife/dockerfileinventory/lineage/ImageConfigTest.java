package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

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
}
