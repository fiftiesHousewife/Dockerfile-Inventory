package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseScopeTest {

    private static CatalogueEntry.Place at(String scope) {
        return new CatalogueEntry.Place("payments/server", "Dockerfile", scope, "abc123", null, null);
    }

    @Test
    void aDockerfileAtAnyReleasedCommitIsReleased() {
        assertThat(ReleaseScope.of(List.of(at("DEFAULT_BRANCH"), at("RELEASED")))).isEqualTo("released");
    }

    @Test
    void aDockerfileOnlyOnDefaultBranchesSaysSo() {
        assertThat(ReleaseScope.of(List.of(at("DEFAULT_BRANCH")))).isEqualTo("default branch only");
    }

    @Test
    void aDockerfileWithNoLocationsHasNoScope() {
        assertThat(ReleaseScope.of(List.of())).isNull();
    }
}
