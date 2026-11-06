package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ReportRunTest {

    @TempDir
    Path out;

    @Test
    void writesPassesArchetypesTablesDdlAndDashboard() throws IOException {
        new ReportRun(EstateTest.FIXTURES, EstateTest.FIXTURES, null, EolList.empty(), out).run();

        JsonNode passes = new ObjectMapper().readTree(out.resolve("passes.json").toFile());
        assertThat(passes.path("passes").size()).isEqualTo(4);
        assertThat(passes.path("k80").asInt()).isEqualTo(2);
        JsonNode archetypes = new ObjectMapper().readTree(out.resolve("archetypes.json").toFile());
        assertThat(archetypes.path("archetypes").size()).isEqualTo(2);
        assertThat(Files.readString(out.resolve("tables/dockerfiles.csv"))).startsWith("blob_sha,");
        assertThat(Files.readString(out.resolve("tables/ddl.sql")))
                .contains("CREATE TABLE dockerfiles").contains("CREATE TABLE bases");
        assertThat(Files.readString(out.resolve("dashboard.html"))).startsWith("<!doctype html>");
    }
}
