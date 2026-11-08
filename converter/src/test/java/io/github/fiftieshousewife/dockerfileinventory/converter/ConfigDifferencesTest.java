package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConfigDifferencesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aChangedFieldAndAnAddedLabelAreDifferencesAndBuildTimeLabelsAreNot() {
        JsonNode original = JSON.readTree("""
                {"User": "1001", "Entrypoint": ["app"], "Labels": {"a": "1", "io.buildah.version": "1.37.0"}}""");
        JsonNode regenerated = JSON.readTree("""
                {"User": "0", "Entrypoint": ["app"],
                 "Labels": {"a": "1", "b": "2", "io.buildah.version": "1.38.0"}}""");

        assertThat(ConfigDifferences.of(original, regenerated))
                .containsExactly("label b: (none) became \"2\"", "User: \"1001\" became \"0\"");
    }

    @Test
    void theBuildContainersHostnameIsNotADifference() {
        assertThat(ConfigDifferences.of(JSON.readTree("{\"Hostname\": \"d633421eacc7\", \"User\": \"1001\"}"),
                JSON.readTree("{\"Hostname\": \"b287ef48c7ae\", \"User\": \"1001\"}"))).isEmpty();
    }
}
