package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

record CatalogueView(List<CatalogueEntry> entries) {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    CatalogueView {
        entries = List.copyOf(entries);
    }

    static CatalogueView read(Path catalogue) {
        try {
            return new CatalogueView(Files.readAllLines(catalogue).stream().filter(line -> !line.isBlank())
                    .map(line -> JSON.readValue(line, CatalogueEntry.class)).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String organisation(CatalogueEntry entry) {
        String repository = entry.locations().isEmpty() ? "" : entry.locations().getFirst().repository();
        return repository.contains("/") ? repository.substring(0, repository.indexOf('/')) : repository;
    }
}
