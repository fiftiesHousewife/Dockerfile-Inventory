package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

final class ReportRun {

    private final Path factsDir;
    private final Path discoveryDir;
    private final Path lineageDir;
    private final EolList eol;
    private final Path outDir;
    private final ObjectMapper json = new ObjectMapper();

    ReportRun(Path factsDir, Path discoveryDir, Path lineageDir, EolList eol, Path outDir) {
        this.factsDir = factsDir;
        this.discoveryDir = discoveryDir;
        this.lineageDir = lineageDir;
        this.eol = eol;
        this.outDir = outDir;
    }

    void run() {
        Estate estate = Estate.read(factsDir, discoveryDir, lineageDir);
        List<Group> archetypeGroups = NearDuplicates.of(Passes.signatureGroups(estate));
        Ranking ranking = Archetypes.rank(archetypeGroups, estate);
        List<BaseRow> bases = BaseCatalogue.of(estate, eol);
        List<PassCount> passes = new ArrayList<>(Passes.exact(estate));
        passes.add(new PassCount("near-duplicate", archetypeGroups.size(), ranking.totalLocations()));
        List<Table> tables = Tables.of(estate, archetypeGroups, ranking, bases);

        write("passes.json", pretty(passesSummary(ranking, passes)));
        write("archetypes.json", pretty(ranking));
        tables.forEach(t -> write("tables/" + t.name() + ".csv", t.csv()));
        write("tables/ddl.sql", tables.stream().map(Table::ddl).collect(Collectors.joining("\n")));
        write("dashboard.html", Dashboard.render(
                Summary.of(estate, passes.subList(0, 3), archetypeGroups.size(), ranking, bases), ranking,
                ArchetypeCards.of(ranking, estate, discoveryDir), bases,
                Optional.ofNullable(lineageDir).map(dir -> LineageFiles.read(dir, locations(estate)))));
    }

    static Map<String, Object> passesSummary(Ranking ranking, List<PassCount> passes) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalLocations", ranking.totalLocations());
        summary.put("k80", ranking.k80());
        summary.put("k95", ranking.k95());
        summary.put("passes", passes);
        return summary;
    }

    static Map<String, List<Location>> locations(Estate estate) {
        Map<String, List<Location>> locations = new HashMap<>();
        estate.dockerfiles().forEach(d -> locations.put(d.blobSha(), d.locations()));
        estate.failures().forEach(f -> locations.put(f.blobSha(), f.locations()));
        return locations;
    }

    private String pretty(Object value) {
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private void write(String file, String text) {
        try {
            Files.createDirectories(outDir.resolve("tables"));
            Files.writeString(outDir.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
