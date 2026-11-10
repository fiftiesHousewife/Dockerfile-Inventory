package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ReportRunTest {

    @TempDir
    Path out;

    @Test
    void writesPassesArchetypesAsJsonLinesAndThePagesButNoTables() throws IOException {
        new ReportRun(EstateTest.FIXTURES, EstateTest.FIXTURES, null, null, null, CatalogueSettings.DEFAULTS, out)
                .run();

        JsonNode passes = new ObjectMapper().readTree(out.resolve("passes.json").toFile());
        List<JsonNode> archetypes = JsonLines.read(out.resolve("archetypes.jsonl"));
        assertAll(
                () -> assertThat(passes.path("passes").size()).isEqualTo(4),
                () -> assertThat(passes.path("k80").asInt()).isEqualTo(2),
                () -> assertThat(archetypes).extracting(a -> a.path("rank").asInt()).containsExactly(1, 2),
                () -> assertThat(out.resolve("tables")).doesNotExist(),
                () -> assertThat(out.resolve("archetypes.json")).doesNotExist(),
                () -> assertThat(read("index.html")).startsWith("<!doctype html>"),
                () -> assertThat(read("orgs/payments.html")).contains("<h1>payments</h1>"),
                () -> assertThat(read("hierarchy.html")).contains("<h1>Hierarchy</h1>"),
                () -> assertThat(out.resolve("archetypes")).doesNotExist(),
                () -> assertThat(out.resolve("dashboard.html")).doesNotExist());
    }

    @Test
    void theCatalogueIsWrittenBesideTheReport() {
        new ReportRun(EstateTest.FIXTURES, EstateTest.FIXTURES, null, null, null, CatalogueSettings.DEFAULTS, out)
                .run();

        assertThat(out.resolve("catalogue/dockerfiles.jsonl")).isNotEmptyFile();
    }

    @Test
    void theCatalogueAloneWritesTheCatalogueTheArchetypesAndThePassesButNoPage() {
        new ReportRun(EstateTest.FIXTURES, EstateTest.FIXTURES, null, null, null, CatalogueSettings.DEFAULTS, out)
                .catalogue();

        assertAll(
                () -> assertThat(out.resolve(CatalogueRun.PATH)).exists(),
                () -> assertThat(out.resolve("archetypes.jsonl")).exists(),
                () -> assertThat(out.resolve("passes.json")).exists(),
                () -> assertThat(out.resolve("index.html")).doesNotExist());
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

    @Test
    void aFileInAFolderIsWrittenWithItsFolder() {
        new ReportRun(null, null, null, null, null, CatalogueSettings.DEFAULTS, out).write("pages/a.html", "a");

        assertThat(out.resolve("pages/a.html")).hasContent("a");
    }

    @Test
    void aFileAtTheTopIsWrittenInTheOutputFolder() {
        new ReportRun(null, null, null, null, null, CatalogueSettings.DEFAULTS, out).write("b.json", "b");

        assertThat(out.resolve("b.json")).hasContent("b");
    }
}
