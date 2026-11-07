package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ArchetypeCards {

    private static final int CARDS = 10;

    record Card(int rank, String sentence, int files, int locations, List<String> repositories,
                String exampleLocation, String exampleText) {

        Card {
            repositories = List.copyOf(repositories);
        }
    }

    private ArchetypeCards() {
    }

    static List<Card> of(Ranking ranking, Estate estate, Path discoveryDir) {
        Map<String, Dockerfile> byBlob = estate.dockerfiles().stream()
                .collect(Collectors.toMap(Dockerfile::blobSha, d -> d, (a, b) -> a));
        Map<String, List<Location>> locations = new HashMap<>();
        estate.failures().forEach(f -> locations.put(f.blobSha(), f.locations()));
        estate.dockerfiles().forEach(d -> locations.put(d.blobSha(), d.locations()));
        return ranking.archetypes().stream().limit(CARDS)
                .map(a -> card(a, byBlob.get(a.representative()),
                        locations.getOrDefault(a.representative(), List.of()), discoveryDir))
                .toList();
    }

    static Card card(Archetype archetype, Dockerfile example, List<Location> where, Path discoveryDir) {
        String location = location(where);
        String text = location == null ? null
                : exampleText(discoveryDir.resolve("blobs").resolve(archetype.representative()));
        return new Card(archetype.rank(), ArchetypeSentence.of(archetype.signature(), baseName(example)),
                archetype.blobShas().size(), archetype.locations(), archetype.repositories(), location, text);
    }

    static String baseName(Dockerfile example) {
        return example == null ? "an unknown base" : example.base().name()
                + (example.base().tag() == null ? "" : ":" + example.base().tag());
    }

    static String location(List<Location> where) {
        return where.isEmpty() ? null : where.getFirst().repository() + ": " + where.getFirst().path();
    }

    static String exampleText(Path blob) {
        try {
            return Files.exists(blob) ? HiddenSecrets.hidden(Files.readString(blob)) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
