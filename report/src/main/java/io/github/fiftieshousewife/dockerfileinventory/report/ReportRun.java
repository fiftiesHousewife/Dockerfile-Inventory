package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 2 end to end: read the estate, run the passes, rank, catalogue, and write every output under the output
 * directory: {@code passes.json}, {@code archetypes.json}, {@code tables/*.csv} with {@code tables/ddl.sql}, and
 * {@code dashboard.html}. Every file is rewritten from its inputs each run.
 */
public final class ReportRun {

    private final Path factsDir;
    private final Path discoveryDir;
    private final Path lineageDir;
    private final EolList eol;
    private final Path outDir;
    private final ObjectMapper json = new ObjectMapper();

    public ReportRun(Path factsDir, Path discoveryDir, Path lineageDir, EolList eol, Path outDir) {
        this.factsDir = factsDir;
        this.discoveryDir = discoveryDir;
        this.lineageDir = lineageDir;
        this.eol = eol;
        this.outDir = outDir;
    }

    public void run() {
        Estate estate = Estate.read(factsDir, discoveryDir, lineageDir);
        List<Group> archetypeGroups = NearDuplicates.of(Passes.signatureGroups(estate));
        Ranking ranking = Archetypes.rank(archetypeGroups, estate);
        List<BaseRow> bases = BaseCatalogue.of(estate, eol);
        List<PassCount> passes = new ArrayList<>(Passes.exact(estate));
        passes.add(new PassCount("near-duplicate", archetypeGroups.size(), ranking.totalLocations()));
        List<Table> tables = Tables.of(estate, archetypeGroups, ranking, bases);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalLocations", ranking.totalLocations());
        summary.put("k80", ranking.k80());
        summary.put("k95", ranking.k95());
        summary.put("passes", passes);
        write("passes.json", pretty(summary));
        write("archetypes.json", pretty(ranking));
        tables.forEach(t -> write("tables/" + t.name() + ".csv", t.csv()));
        write("tables/ddl.sql", tables.stream().map(Table::ddl).collect(Collectors.joining("\n")));
        write("dashboard.html", Dashboard.render(
                Summary.of(estate, passes.subList(0, 3), archetypeGroups.size(), ranking, bases), ranking, bases));
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
