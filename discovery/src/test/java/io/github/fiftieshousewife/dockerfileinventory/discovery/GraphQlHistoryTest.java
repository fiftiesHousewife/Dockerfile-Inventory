package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GraphQlHistoryTest {

    static final String COMMIT = "c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0";
    static final ChangeRequest LEDGER = new ChangeRequest("payments/ledger-api", COMMIT, "Dockerfile");
    static final ChangeRequest NO_HISTORY = new ChangeRequest("payments/ledger-api", COMMIT, "build/Containerfile");
    static final ChangeRequest GONE = new ChangeRequest("payments/old", COMMIT, "Dockerfile");

    @Test
    void asksOncePerRepositoryAndCommitWithOneHistoryPerPath() {
        String query = GraphQlHistory.query(List.of(LEDGER, NO_HISTORY, GONE));

        assertAll(
                () -> assertThat(query).startsWith("query {\n").endsWith("\n}"),
                () -> assertThat(query).containsOnlyOnce("r0: repository(owner: \"payments\", name: \"ledger-api\")"
                        + " { object(oid: \"" + COMMIT + "\") { ... on Commit {"),
                () -> assertThat(query).contains("p0: history(first: 1, path: \"Dockerfile\")"
                        + " { nodes { oid committedDate } }"),
                () -> assertThat(query).contains("p1: history(first: 1, path: \"build/Containerfile\")"),
                () -> assertThat(query).contains("r1: repository(owner: \"payments\", name: \"old\")"));
    }

    @Test
    void writesPathsAsEscapedStringLiterals() {
        assertThat(GraphQlHistory.query(List.of(new ChangeRequest("pay/api", COMMIT, "odd\"name/Dockerfile"))))
                .contains("path: \"odd\\\"name/Dockerfile\"");
    }

    @Test
    void readsEachLocationsLastChangeAndAnEmptyOneWhereGitHubHasNone() {
        var data = new ObjectMapper().readTree(RecordedGitHub.fixture("history.json")).path("data");

        assertThat(GraphQlHistory.read(data, List.of(LEDGER, NO_HISTORY, GONE))).containsExactly(
                new LastChange("payments/ledger-api", COMMIT, "Dockerfile", "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1",
                        "2026-03-02T10:11:12Z"),
                new LastChange("payments/ledger-api", COMMIT, "build/Containerfile", null, null),
                new LastChange("payments/old", COMMIT, "Dockerfile", null, null));
    }
}
