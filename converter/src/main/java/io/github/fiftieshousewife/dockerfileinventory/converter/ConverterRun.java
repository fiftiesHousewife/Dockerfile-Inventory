package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.ImageBuilder;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Policy;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PolicyViolation;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 5 end to end. Every distinct Dockerfile in discovery's {@code blobs/} is converted and round-trip checked,
 * which gives estate-wide coverage; only Dockerfiles in opted-in repositories ({@code OPT_IN}, one
 * {@code owner/repo} per line) get a generated class, under {@code generated/images/<owner>/}. Writes
 * {@code roundtrip.jsonl}, {@code coverage.json} (typed instructions over all, per image, and a histogram of raw
 * reasons, the builder's backlog) and the classes. Without an approved list, S1 is left out of the TODOs.
 */
public final class ConverterRun {

    private record Location(String repository, String path) {
    }

    private record Row(String blobSha, boolean passed, String difference) {
    }

    private final Path discoveryDir;
    private final Path optIn;
    private final Path allowList;
    private final Path outDir;
    private final ObjectMapper json = new ObjectMapper();

    public ConverterRun(Path discoveryDir, Path optIn, Path allowList, Path outDir) {
        this.discoveryDir = discoveryDir;
        this.optIn = optIn;
        this.allowList = allowList;
        this.outDir = outDir;
    }

    public void run() {
        Set<String> optedIn = optIn == null ? Set.of() : read(optIn).lines().map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#")).collect(Collectors.toSet());
        Policy policy = allowList == null ? Policy.of(List.of()) : Policy.read(allowList);
        Map<String, List<Location>> locations = locations();
        List<Row> rows = new ArrayList<>();
        Map<String, Double> coverage = new TreeMap<>();
        Map<String, Integer> reasons = new TreeMap<>();
        FactsParser parser = new FactsParser();
        for (Path blob : blobs()) {
            String sha = String.valueOf(blob.getFileName());
            String text = read(blob);
            if (!(parser.parse(sha, text) instanceof ParseResult.Parsed parsed)) {
                rows.add(new Row(sha, false, "the Dockerfile does not parse"));
                continue;
            }
            Conversion conversion;
            try {
                conversion = Convert.of(parsed.file());
            } catch (IllegalArgumentException e) {
                rows.add(new Row(sha, false, "conversion failed: " + e.getMessage()));
                continue;
            }
            coverage.put(sha, coverage(conversion));
            conversion.raw().forEach(raw -> reasons.merge(raw.reason(), 1, Integer::sum));
            RoundTrip result = RoundTrip.check(text, conversion.model());
            rows.add(new Row(sha, result.passed(), result.difference()));
            if (result.passed()) {
                List<PolicyViolation.Breach> breaches = ImageBuilder.of(conversion.model()).check(policy).stream()
                        .filter(b -> allowList != null || !b.rule().equals("S1"))
                        .toList();
                locations.getOrDefault(sha, List.of()).stream()
                        .filter(l -> optedIn.contains(l.repository()))
                        .forEach(l -> generate(l, conversion, breaches));
            }
        }
        write("roundtrip.jsonl", rows.stream().map(json::writeValueAsString).collect(Collectors.joining("\n"))
                + (rows.isEmpty() ? "" : "\n"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("images", coverage);
        summary.put("rawReasons", reasons);
        write("coverage.json", json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
    }

    /** Typed instructions over all instructions after {@code FROM}. */
    static double coverage(Conversion conversion) {
        List<Instruction> all = conversion.model().stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .toList();
        long typed = all.stream().filter(i -> !(i instanceof Instruction.Raw)).count();
        return all.isEmpty() ? 1.0 : (double) typed / all.size();
    }

    private void generate(Location location, Conversion conversion, List<PolicyViolation.Breach> breaches) {
        String owner = location.repository().split("/", 2)[0];
        String packageName = "images." + owner.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
        String className = className(location);
        write("generated/" + packageName.replace('.', '/') + "/" + className + ".java",
                JavaEmitter.emit(packageName, className, conversion, breaches));
    }

    /**
     * Owner, repository, and the path's folders and suffix, in camel case: {@code payments/api} with
     * {@code Dockerfile} gives {@code PaymentsApiImage}.
     */
    static String className(Location location) {
        List<String> parts = new ArrayList<>(List.of(location.repository().split("/")));
        List<String> path = new ArrayList<>(List.of(location.path().split("/")));
        String file = path.removeLast();
        parts.addAll(path);
        if (file.contains(".")) {
            parts.add(file.substring(file.indexOf('.') + 1));
        }
        String name = parts.stream()
                .flatMap(p -> Stream.of(p.split("[^A-Za-z0-9]+")))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining());
        return (Character.isDigit(name.charAt(0)) ? "Image" + name : name) + "Image";
    }

    private Map<String, List<Location>> locations() {
        Map<String, List<Location>> locations = new LinkedHashMap<>();
        Path manifest = discoveryDir.resolve("manifest.jsonl");
        if (Files.exists(manifest)) {
            read(manifest).lines().filter(l -> !l.isBlank()).map(json::readTree).forEach(row -> locations
                    .computeIfAbsent(row.path("blobSha").asString(), k -> new ArrayList<>())
                    .add(new Location(row.path("repository").asString(), row.path("path").asString())));
        }
        return locations;
    }

    private List<Path> blobs() {
        try (Stream<Path> files = Files.list(discoveryDir.resolve("blobs"))) {
            return files.filter(f -> Files.isRegularFile(f) && !String.valueOf(f.getFileName()).endsWith(".part"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(String file, String text) {
        try {
            Path target = outDir.resolve(file);
            Path folder = outDir.resolve(file.contains("/") ? file.substring(0, file.lastIndexOf('/')) : "");
            Files.createDirectories(folder);
            Files.writeString(target, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
