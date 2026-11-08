package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.plural;

import io.github.fiftieshousewife.dockerfileinventory.report.ArchetypeRows.Row;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ArchetypePage {

    private static final int SHA_SHOWN = 12;

    private ArchetypePage() {
    }

    static String body(Row row, Map<String, List<Location>> locations, Path discoveryDir) {
        String representative = representative(row, locations);
        String example = example(discoveryDir.resolve("blobs").resolve(representative));
        Location where = locations.getOrDefault(representative, List.of()).stream().findFirst().orElse(null);
        return "<p class=\"note\">" + plural(row.locations(), "location", "locations") + " in "
                + plural(row.files(), "file", "files") + ". Rank " + row.archetype().rank()
                + " in archetypes.jsonl.</p>" + signature(row.archetype().signature())
                + "<h2>Organisations</h2>" + CountTable.render("Organisation", row.organisations(), Map.of(),
                        Integer.MAX_VALUE)
                + "<h2>Repositories</h2>" + CountTable.render("Repository", row.repositories(), Map.of(),
                        Integer.MAX_VALUE)
                + (example.isEmpty() || where == null ? "" : "<h2>Example</h2><details><summary>The most used "
                        + "Dockerfile in it: " + escape(where.repository() + ": " + where.path()) + "</summary><pre>"
                        + escape(example) + "</pre></details>")
                + "<h2>Files (" + row.files() + ")</h2><ul class=\"files\">" + row.blobShas().stream()
                        .map(b -> file(b, locations.getOrDefault(b, List.of()))).collect(Collectors.joining())
                + "</ul>";
    }

    static String signature(String signature) {
        return "<table class=\"attributes\">" + ArchetypeName.attributes(signature).entrySet().stream()
                .map(e -> "<tr><th>" + escape(e.getKey()) + "</th><td>" + escape(e.getValue()) + "</td></tr>")
                .collect(Collectors.joining()) + "</table>";
    }

    private static String file(String blob, List<Location> where) {
        return "<li><code>" + escape(blob.substring(0, Math.min(SHA_SHOWN, blob.length()))) + "</code>: "
                + escape(where.stream().map(l -> l.repository() + ": " + l.path()).collect(Collectors.joining("; ")))
                + "</li>";
    }

    static String representative(Row row, Map<String, List<Location>> locations) {
        return row.blobShas().contains(row.archetype().representative()) ? row.archetype().representative()
                : row.blobShas().stream().max(Comparator.comparingInt((String b) ->
                        locations.getOrDefault(b, List.of()).size()).thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }

    static String example(Path blob) {
        try {
            return Files.exists(blob) ? HiddenSecrets.hidden(Files.readString(blob)) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
