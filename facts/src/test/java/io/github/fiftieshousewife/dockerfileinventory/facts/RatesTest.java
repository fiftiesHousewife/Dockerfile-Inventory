package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RatesTest {

    private static FactsRun.FactRow row(String blobSha, String dockerfile) {
        return new FactsRun.FactRow(blobSha, "t", "s", "sig", FactExtractorTest.facts(dockerfile), List.of(), null,
                false);
    }

    @Test
    void unresolvedBasesCountOncePerOrganisationAndBlob() {
        List<FactsRun.FactRow> facts = List.of(row("open", "ARG B\nFROM $B\n"), row("pinned", "FROM ubi9\n"));
        List<Map.Entry<String, String>> locations = List.of(Map.entry("a/one", "open"), Map.entry("a/two", "open"),
                Map.entry("b/one", "open"), Map.entry("a/three", "pinned"));

        assertThat(Rates.unresolvedByOrganisation(facts, locations)).containsExactly(Map.entry("a", 1),
                Map.entry("b", 1));
    }

    @Test
    void featureCountsCountBlobsUsingEachFeature() {
        List<FactsRun.FactRow> facts = List.of(row("a", "FROM ubi9\nRUN <<EOF\nx\nEOF\n"),
                row("b", "FROM ubi9\nRUN --mount=type=cache,target=/c <<EOF\nx\nEOF\n"));

        assertThat(Rates.featureCounts(facts)).containsExactly(Map.entry("heredoc", 2), Map.entry("run-mount", 1));
    }

    @Test
    void ratioIsZeroForNothingToMeasure() {
        assertThat(Rates.ratio(1, 4)).isEqualTo(0.25);
        assertThat(Rates.ratio(0, 0)).isZero();
    }
}
