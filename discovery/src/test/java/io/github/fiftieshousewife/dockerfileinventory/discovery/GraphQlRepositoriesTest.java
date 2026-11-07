package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GraphQlRepositoriesTest {

    @Test
    void followsTheEndCursorWhileThereIsANextPage() {
        assertThat(GraphQlRepositories.nextCursor(page("repositories-page1.json"))).contains("Y3Vyc29yOjE=");
    }

    @Test
    void stopsOnTheLastPage() {
        assertThat(GraphQlRepositories.nextCursor(page("repositories-page2.json"))).isEmpty();
    }

    @Test
    void readsAnEmptyArchivedForkWithNoDefaultBranch() {
        JsonNode node = page("repositories-page2.json").path("nodes").get(0);

        assertThat(GraphQlRepositories.repository(node))
                .isEqualTo(new Repository("payments/empty", null, null, true, true, null));
    }

    private static JsonNode page(String fixture) {
        return new ObjectMapper().readTree(RecordedGitHub.fixture(fixture))
                .path("data").path("organization").path("repositories");
    }
}
