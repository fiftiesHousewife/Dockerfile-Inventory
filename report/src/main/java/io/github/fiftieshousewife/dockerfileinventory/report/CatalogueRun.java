package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Secrets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

final class CatalogueRun {

    private static final ObjectMapper JSON = new ObjectMapper();

    private CatalogueRun() {
    }

    static void write(Path factsDir, Path discoveryDir, Path lineageDir, Path outDir) {
        Map<String, List<CatalogueEntry.Place>> places =
                CatalogueEntries.places(discoveryDir.resolve("manifest.jsonl"));
        List<CatalogueEntry> parsed = JsonLines.read(factsDir.resolve("facts.jsonl")).stream()
                .map(row -> CatalogueEntries.of(row, places.get(row.path("blobSha").asString())))
                .toList();
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(parsed, lineageDir);
        List<CatalogueEntry> entries = new ArrayList<>(parsed.stream()
                .map(entry -> entry.placed(links.get(entry.blobSha()),
                        Footprint.through(entry.blobSha(), parsed, links),
                        Runtimes.inherited(entry.blobSha(), parsed, links)))
                .toList());
        JsonLines.read(factsDir.resolve("parse-failures.jsonl")).forEach(failure -> entries.add(
                CatalogueEntries.unparsed(failure, places.get(failure.path("blobSha").asString()))));
        try {
            Files.createDirectories(outDir.resolve("catalogue"));
            Files.writeString(outDir.resolve("catalogue/dockerfiles.jsonl"), entries.stream()
                    .map(entry -> guarded(JSON.writeValueAsString(entry))).collect(Collectors.joining("\n", "", "\n")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Values reach the catalogue already hidden by facts; a secret-shaped one that slipped through is hidden here.
    static String guarded(String line) {
        return Secrets.hideShapes(line);
    }
}
