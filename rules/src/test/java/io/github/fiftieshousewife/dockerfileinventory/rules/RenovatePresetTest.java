package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RenovatePresetTest {

    @Test
    void thePresetPinsDockerfileBasesByDigestAndGroupsTheirUpdates() {
        JsonNode preset = new ObjectMapper().readTree(Path.of("../renovate/dockerfile-estate.json").toFile());

        JsonNode rule = preset.path("packageRules").path(0);
        assertThat(rule.path("matchManagers").path(0).asString()).isEqualTo("dockerfile");
        assertThat(rule.path("pinDigests").asBoolean()).isTrue();
        assertThat(rule.path("groupName").asString()).isNotBlank();
    }
}
