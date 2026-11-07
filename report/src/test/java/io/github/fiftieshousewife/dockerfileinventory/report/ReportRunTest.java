package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ReportRunTest {

    @TempDir
    Path out;

    @Test
    void writesPassesArchetypesTablesDdlAndThePages() throws IOException {
        new ReportRun(EstateTest.FIXTURES, EstateTest.FIXTURES, null, EolList.empty(), out).run();

        JsonNode passes = new ObjectMapper().readTree(out.resolve("passes.json").toFile());
        JsonNode archetypes = new ObjectMapper().readTree(out.resolve("archetypes.json").toFile());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(passes.path("passes").size()).isEqualTo(4);
            softly.assertThat(passes.path("k80").asInt()).isEqualTo(2);
            softly.assertThat(archetypes.path("archetypes").size()).isEqualTo(2);
            softly.assertThat(read("tables/dockerfiles.csv")).startsWith("blob_sha,");
            softly.assertThat(read("tables/ddl.sql"))
                    .contains("CREATE TABLE dockerfiles").contains("CREATE TABLE bases");
            softly.assertThat(read("index.html")).startsWith("<!doctype html>");
            softly.assertThat(read("orgs/payments.html")).contains("<h1>payments</h1>");
            softly.assertThat(read("archetypes/1.html")).contains("<h1>Archetype 1: ");
            softly.assertThat(out.resolve("dashboard.html")).doesNotExist();
        });
    }

    @Test
    void passesJsonHoldsTheTotalsBeforeThePasses() {
        List<PassCount> passes = List.of(new PassCount("blob", 1, 2));

        assertThat(ReportRun.passesSummary(new Ranking(List.of(), 2, 1, 1), passes)).containsExactly(
                Map.entry("totalLocations", 2), Map.entry("k80", 1), Map.entry("k95", 1), Map.entry("passes", passes));
    }

    @Test
    void locationsAreKeyedByBlobForParsedAndFailedFilesAlike() {
        Estate estate = new Estate(List.of(PassesTest.dockerfile("a", "t", "s", 1)),
                List.of(PassesTest.failure("bad", 2)), List.of(), Map.of());

        assertThat(ReportRun.locations(estate)).containsOnlyKeys("a", "bad")
                .hasEntrySatisfying("bad", locations -> assertThat(locations).hasSize(2));
    }

    private String read(String file) {
        try {
            return Files.readString(out.resolve(file));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
