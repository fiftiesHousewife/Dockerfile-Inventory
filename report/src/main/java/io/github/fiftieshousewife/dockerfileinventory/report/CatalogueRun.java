package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.secrets.Secrets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

final class CatalogueRun {

    static final String PATH = "catalogue/dockerfiles.jsonl";

    private static final ObjectMapper JSON = new ObjectMapper();

    private CatalogueRun() {
    }

    record Inputs(Path factsDir, Path discoveryDir, Path lineageDir, Path converterDir, List<Group> nearDuplicates) {

        Inputs {
            nearDuplicates = List.copyOf(nearDuplicates);
        }
    }

    static void write(Inputs inputs, Path outDir, CatalogueSettings settings) {
        Map<String, List<CatalogueEntry.Place>> places = CatalogueEntries.places(inputs.discoveryDir());
        List<CatalogueEntry> parsed = parsed(inputs.factsDir(), places);
        EntryPlacer placer = new EntryPlacer(parsed, inputs, settings);
        writeLines(outDir, Stream.concat(parsed.stream().map(placer::placed),
                unparsed(inputs.factsDir(), places).stream()).toList());
    }

    static List<CatalogueEntry> parsed(Path factsDir, Map<String, List<CatalogueEntry.Place>> places) {
        return JsonLines.read(factsDir.resolve("facts.jsonl")).stream()
                .map(row -> CatalogueEntries.of(row, places.get(row.path("blobSha").asString()))).toList();
    }

    static List<CatalogueEntry> unparsed(Path factsDir, Map<String, List<CatalogueEntry.Place>> places) {
        return JsonLines.read(factsDir.resolve("parse-failures.jsonl")).stream()
                .map(failure -> CatalogueEntries.unparsed(failure, places.get(failure.path("blobSha").asString())))
                .toList();
    }

    static void writeLines(Path outDir, List<CatalogueEntry> entries) {
        try {
            Files.createDirectories(outDir.resolve("catalogue"));
            Files.writeString(outDir.resolve(PATH), entries.stream()
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
