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
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

final class ReportRun {

    private final Path factsDir;
    private final Path discoveryDir;
    private final Path lineageDir;
    private final Path converterDir;
    private final CatalogueSettings catalogueSettings;
    private final Path outDir;
    private final ObjectMapper json = new ObjectMapper();

    ReportRun(Path factsDir, Path discoveryDir, Path lineageDir, Path converterDir,
              CatalogueSettings catalogueSettings, Path outDir) {
        this.factsDir = factsDir;
        this.discoveryDir = discoveryDir;
        this.lineageDir = lineageDir;
        this.converterDir = converterDir;
        this.catalogueSettings = catalogueSettings;
        this.outDir = outDir;
    }

    void run() {
        Estate estate = Estate.read(factsDir, discoveryDir, lineageDir);
        List<Group> archetypeGroups = NearDuplicates.of(Passes.signatureGroups(estate));
        Ranking ranking = Archetypes.rank(archetypeGroups, estate);
        List<PassCount> passes = new ArrayList<>(Passes.exact(estate));
        passes.add(new PassCount("near-duplicate", archetypeGroups.size(), ranking.totalLocations()));

        write("passes.json", pretty(passesSummary(ranking, passes)));
        write("archetypes.jsonl", ranking.archetypes().stream().map(json::writeValueAsString)
                .collect(Collectors.joining("\n", "", "\n")));
        CatalogueRun.write(new CatalogueRun.Inputs(factsDir, discoveryDir, lineageDir, converterDir, archetypeGroups),
                outDir, catalogueSettings);
        Pages.all(CatalogueView.read(outDir.resolve(CatalogueRun.PATH)), ArchetypeRows.of(ranking, estate))
                .forEach(this::write);
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
            int slash = file.lastIndexOf('/');
            Files.createDirectories(slash < 0 ? outDir : outDir.resolve(file.substring(0, slash)));
            Files.writeString(outDir.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
