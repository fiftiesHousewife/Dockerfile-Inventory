package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Secrets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

final class CatalogueRun {

    private static final ObjectMapper JSON = new ObjectMapper();

    private CatalogueRun() {
    }

    static void write(Path factsDir, Path discoveryDir, Path lineageDir, Path outDir, CatalogueSettings settings) {
        Map<String, List<CatalogueEntry.Place>> places =
                CatalogueEntries.places(discoveryDir);
        List<CatalogueEntry> parsed = JsonLines.read(factsDir.resolve("facts.jsonl")).stream()
                .map(row -> CatalogueEntries.of(row, places.get(row.path("blobSha").asString())))
                .toList();
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(parsed, lineageDir);
        Hierarchies hierarchies = new Hierarchies(lineageDir == null ? Map.of() : RegistryImages.read(lineageDir),
                settings.staleAfterMonths(), settings.layerLimit());
        Set<String> archived = Unmaintained.archivedRepositories(discoveryDir);
        List<CatalogueEntry> entries = new ArrayList<>(parsed.stream()
                .map(entry -> entry.placed(links.get(entry.blobSha()),
                        Footprint.through(entry.blobSha(), parsed, links),
                        Runtimes.inherited(entry.blobSha(), parsed, links),
                        hierarchies.of(entry, parsed, links, archived)))
                .toList());
        JsonLines.read(factsDir.resolve("parse-failures.jsonl")).forEach(failure -> entries.add(
                CatalogueEntries.unparsed(failure, places.get(failure.path("blobSha").asString()))));
        try {
            Files.createDirectories(outDir.resolve("catalogue"));
            Files.writeString(outDir.resolve("catalogue/dockerfiles.jsonl"), entries.stream()
                    .map(entry -> hideSecretShapesMissedUpstream(JSON.writeValueAsString(entry)))
                    .collect(Collectors.joining("\n", "", "\n")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String hideSecretShapesMissedUpstream(String line) {
        return Secrets.hideShapes(line);
    }
}
