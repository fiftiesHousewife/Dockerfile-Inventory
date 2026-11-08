package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DockerfileSelectionTest {

    private static final String SHA = "04f0a7c54544df610e5d20cc12f3ce687a5a2c73";
    private static final String OTHER = "17249dbcb3ddc6830cd9982ec3be980ea2471ad0";
    private static final List<JsonNode> MANIFEST = List.of(
            row("bitnami/containers", "bitnami/notation/1/debian-12/Dockerfile", SHA),
            row("payments/api", "Dockerfile", OTHER));

    @Test
    void aLineIsABlobShaOrARepositoryAndPathAndCommentsAndBlankLinesAreSkipped() {
        assertThat(DockerfileSelection.shas(List.of("# the payments service", "", SHA + "  # bitnami",
                "payments/api Dockerfile"), MANIFEST)).containsExactly(SHA, OTHER);
    }

    @Test
    void aLineThatNamesNoDockerfileInTheManifestIsRefusedWithTheLine() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DockerfileSelection.shas(List.of("payments/api build/Dockerfile"), MANIFEST))
                .withMessageContaining("payments/api build/Dockerfile");
    }

    private static JsonNode row(String repository, String path, String sha) {
        return new ObjectMapper().readTree("{\"repository\": \"" + repository + "\", \"path\": \"" + path
                + "\", \"blobSha\": \"" + sha + "\"}");
    }
}
