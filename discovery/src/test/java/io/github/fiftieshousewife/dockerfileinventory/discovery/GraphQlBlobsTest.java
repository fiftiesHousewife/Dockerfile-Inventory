package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GraphQlBlobsTest {

    private static final String FOUND = "d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1";
    private static final String MISSING = "e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2";

    @Test
    void writesGraphqlStringLiteralsEscaped() {
        assertThat(GraphQlBlobs.query(List.of(new BlobRequest("pay/a\"b", "f00d"))))
                .contains("bf00d: repository(owner: \"pay\", name: \"a\\\"b\")");
    }

    @Test
    void asksForEachDistinctBlobOnceUnderItsAlias() {
        var request = new BlobRequest("pay/api", "f00d");

        assertThat(GraphQlBlobs.query(List.of(request, request)))
                .startsWith("query {\n")
                .endsWith("\n}")
                .containsOnlyOnce("bf00d: repository(owner: \"pay\", name: \"api\")");
    }

    @Test
    void readsTheBlobsGitHubFoundAndLeavesOutTheRest() {
        var data = new ObjectMapper().readTree(RecordedGitHub.fixture("blobs.json")).path("data");

        List<BlobText> blobs = GraphQlBlobs.read(data, List.of(
                new BlobRequest("payments/ledger-api", FOUND), new BlobRequest("payments/ledger-api", MISSING)));

        assertThat(blobs).containsExactly(new BlobText(FOUND, "FROM ubi9/ubi-minimal\n", false, false));
    }

    @Test
    void readsABinaryBlobAsHavingNoText() {
        var object = new ObjectMapper().readTree("{\"text\": null, \"isTruncated\": false, \"isBinary\": true}");

        assertThat(GraphQlBlobs.blob("f00d", object)).isEqualTo(new BlobText("f00d", null, false, true));
    }

    @Test
    void quotesBackslashesAndQuotes() {
        assertThat(GraphQlBlobs.quoted("a\\b\"c")).isEqualTo("\"a\\\\b\\\"c\"");
    }

    @Test
    void splitsARepositoryIntoOwnerAndName() {
        assertThat(List.of(GraphQlBlobs.owner("pay/api"), GraphQlBlobs.name("pay/api"))).containsExactly("pay", "api");
    }
}
