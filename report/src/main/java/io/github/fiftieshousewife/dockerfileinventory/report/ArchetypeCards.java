package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The dashboard's largest archetypes, each told as a sentence with its size and one example Dockerfile. */
final class ArchetypeCards {

    private static final int PACKAGES_NAMED = 4;

    /**
     * One archetype as the dashboard shows it. The example is the representative file (a failure's, for the
     * templates and parse-failure groups); its text is null when the file or its location is unknown.
     */
    record Card(int rank, String sentence, int files, int locations, List<String> repositories,
                String exampleLocation, String exampleText) {

        Card {
            repositories = List.copyOf(repositories);
        }
    }

    private static final int CARDS = 10;

    /** The top {@value #CARDS} archetypes, each with its representative Dockerfile as the example. */
    static List<Card> of(Ranking ranking, Estate estate, Path discoveryDir) {
        Map<String, Dockerfile> byBlob = estate.dockerfiles().stream()
                .collect(Collectors.toMap(Dockerfile::blobSha, d -> d, (a, b) -> a));
        Map<String, List<Location>> locations = new HashMap<>();
        estate.failures().forEach(f -> locations.put(f.blobSha(), f.locations()));
        estate.dockerfiles().forEach(d -> locations.put(d.blobSha(), d.locations()));
        return ranking.archetypes().stream().limit(CARDS).map(a -> {
            Dockerfile example = byBlob.get(a.representative());
            String base = example == null ? "an unknown base" : example.base().name()
                    + (example.base().tag() == null ? "" : ":" + example.base().tag());
            List<Location> where = locations.getOrDefault(a.representative(), List.of());
            String location = where.isEmpty() ? null : where.getFirst().repository() + ": " + where.getFirst().path();
            String text = location == null ? null : text(discoveryDir.resolve("blobs").resolve(a.representative()));
            return new Card(a.rank(), sentence(a.signature(), base), a.blobShas().size(), a.locations(),
                    a.repositories(), location, text);
        }).toList();
    }

    private static String text(Path blob) {
        try {
            return Files.exists(blob) ? Files.readString(blob) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ArchetypeCards() {
    }

    /**
     * A signature ({@code base:major|managers|packages|user|ports|entrypoint|stages=N}, as facts'
     * {@code NormalForms.signature} writes it) in plain words, naming the base as the example Dockerfile writes it.
     */
    static String sentence(String signature, String base) {
        if (signature.equals(Passes.TEMPLATES)) {
            return "Templates saved under a Dockerfile's name (Jinja and similar), not Dockerfiles.";
        }
        if (signature.equals(Passes.PARSE_FAILURES)) {
            return "Dockerfiles the parser could not read.";
        }
        String[] f = signature.split("\\|", -1);
        return "Builds on " + base + ", " + installs(f[1], f[2]) + ", " + user(f[3]) + ", " + ports(f[4]) + ", "
                + entrypoint(f[5]) + ", " + stages(f[6]) + ".";
    }

    private static String installs(String managers, String packages) {
        List<String> names = packages.isEmpty() ? List.of() : List.of(packages.split(","));
        if (names.isEmpty()) {
            return "installs no packages";
        }
        String shown = String.join(", ", names.subList(0, Math.min(PACKAGES_NAMED, names.size())));
        String more = names.size() > PACKAGES_NAMED ? " and " + (names.size() - PACKAGES_NAMED) + " more" : "";
        return "installs " + names.size() + (names.size() == 1 ? " package" : " packages") + " with "
                + managers.replace(",", " and ") + " (" + shown + more + ")";
    }

    private static String user(String kind) {
        return switch (kind) {
            case "ROOT" -> "runs as root";
            case "NUMERIC" -> "runs as a numeric user";
            case "NAMED" -> "runs as a named user";
            default -> "runs as a user set by a variable";
        };
    }

    private static String ports(String ports) {
        return ports.isEmpty() ? "exposes no ports" : "exposes " + String.join(" and ", ports.split(","));
    }

    private static String entrypoint(String form) {
        return switch (form) {
            case "EXEC" -> "starts with an exec-form command";
            case "SHELL" -> "starts with a shell-form command";
            default -> "sets no start command of its own";
        };
    }

    private static String stages(String stages) {
        String n = stages.replace("stages=", "");
        return n.equals("1") ? "in one stage" : "in " + n + " stages";
    }
}
