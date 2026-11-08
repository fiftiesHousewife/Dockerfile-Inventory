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

    record Inputs(Path factsDir, Path discoveryDir, Path lineageDir, Path converterDir, List<Group> nearDuplicates) {

        Inputs {
            nearDuplicates = List.copyOf(nearDuplicates);
        }
    }

    static void write(Inputs inputs, Path outDir, CatalogueSettings settings) {
        Path lineageDir = inputs.lineageDir();
        Map<String, List<CatalogueEntry.Place>> places = CatalogueEntries.places(inputs.discoveryDir());
        List<CatalogueEntry> parsed = JsonLines.read(inputs.factsDir().resolve("facts.jsonl")).stream()
                .map(row -> CatalogueEntries.of(row, places.get(row.path("blobSha").asString())))
                .toList();
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(parsed, lineageDir);
        Hierarchies hierarchies = new Hierarchies(lineageDir == null ? Map.of() : RegistryImages.read(lineageDir),
                settings.staleAfterMonths(), settings.layerLimit());
        Set<String> archived = Unmaintained.archivedRepositories(inputs.discoveryDir());
        Map<String, Conversions.Conversion> conversions = Conversions.read(inputs.converterDir());
        Map<String, Siblings.Found> siblings = Siblings.of(inputs.nearDuplicates());
        List<CatalogueEntry> entries = new ArrayList<>(parsed.stream()
                .map(entry -> entry.placed(new Placement(links.get(entry.blobSha()),
                        Footprint.through(entry.blobSha(), parsed, links),
                        Runtimes.inherited(entry.blobSha(), parsed, links),
                        hierarchies.of(entry, parsed, links, archived),
                        new Actions(ReleaseScope.of(entry.locations()),
                                Reaches.of(entry.blobSha(), parsed, links, hierarchies.imagesBelow(entry.blobSha())),
                                Conversions.of(conversions, entry.blobSha()),
                                Siblings.of(siblings, entry.blobSha())))))
                .toList());
        JsonLines.read(inputs.factsDir().resolve("parse-failures.jsonl")).forEach(failure -> entries.add(
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
