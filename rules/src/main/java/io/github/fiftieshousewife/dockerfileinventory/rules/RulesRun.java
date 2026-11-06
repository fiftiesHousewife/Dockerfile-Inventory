package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractor;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 3 end to end, detect-only. Each distinct Dockerfile in discovery's {@code blobs/} is parsed once and every
 * per-file rule runs over it; with lineage output, the effective rules run over every image. Writes
 * {@code findings.jsonl}, {@code tables/findings.csv} (one row per finding per location) with its DDL,
 * {@code rates.json}, and {@code samples/<rule>.csv} for people to classify. A rule whose input is missing (the
 * approved list, the end-of-life list, lineage) is skipped and listed as such.
 */
public final class RulesRun {

    static final int SAMPLE = 50;
    private static final long SEED = 20261106L;

    private record Location(String repository, String path) {
    }

    private final Path discoveryDir;
    private final Path lineageDir;
    private final Path allowList;
    private final Path eolList;
    private final Path outDir;
    private final ObjectMapper json = new ObjectMapper();

    public RulesRun(Path discoveryDir, Path lineageDir, Path allowList, Path eolList, Path outDir) {
        this.discoveryDir = discoveryDir;
        this.lineageDir = lineageDir;
        this.allowList = allowList;
        this.eolList = eolList;
        this.outDir = outDir;
    }

    public void run() {
        List<String> skipped = new ArrayList<>();
        List<Rule> perFile = new ArrayList<>(List.of(new UnpinnedBase(), new UncleanedInstall(),
                new ShellEntrypoint(), new UnversionedPackages(), new Unparseable(), new SecretValue(),
                new PipeToShell(), new AdjacentRuns(), new MissingOciLabels()));
        BaseList allowed = allowList == null ? null : BaseList.read(allowList);
        if (allowed != null) {
            perFile.add(new UnapprovedBase(allowed));
        } else {
            skipped.add("S1");
        }
        if (eolList != null) {
            perFile.add(new EndOfLifeBase(BaseList.read(eolList)));
        } else {
            skipped.add("R2");
        }
        List<Finding> findings = new ArrayList<>(new RuleRun(perFile).run(subjects()));
        if (lineageDir != null) {
            List<EffectiveRule> effective = new ArrayList<>(List.of(new ArbitraryUid()));
            if (allowed != null) {
                effective.add(new UnapprovedRoot(allowed));
            }
            for (ImageView image : LineageView.read(lineageDir)) {
                effective.forEach(rule -> findings.addAll(rule.check(image, this::blobText)));
            }
        } else {
            skipped.add("S4");
        }
        Map<String, List<Location>> locations = locations();
        write("findings.jsonl", findings.stream().map(json::writeValueAsString).collect(Collectors.joining("\n"))
                + (findings.isEmpty() ? "" : "\n"));
        write("tables/findings.csv", table(findings, locations));
        write("tables/ddl.sql", DDL);
        write("rates.json", json.writerWithDefaultPrettyPrinter()
                .writeValueAsString(rates(findings, locations, skipped)));
        samples(findings, locations).forEach((rule, csv) -> write("samples/" + rule + ".csv", csv));
    }

    private static final String DDL = """
            CREATE TABLE findings (
                rule VARCHAR2(4000),
                severity VARCHAR2(4000),
                repository VARCHAR2(4000),
                path VARCHAR2(4000),
                blob_sha VARCHAR2(4000),
                line NUMBER,
                message VARCHAR2(4000),
                value_hash VARCHAR2(4000),
                contributing VARCHAR2(4000)
            );
            """;

    private List<Subject> subjects() {
        FactsParser parser = new FactsParser();
        return blobs().stream().map(blob -> {
            String sha = String.valueOf(blob.getFileName());
            return switch (parser.parse(sha, read(blob))) {
                case ParseResult.Parsed parsed -> Subject.parsed(sha, FactExtractor.extract(parsed.file()),
                        parsed.file());
                case ParseResult.Failed failed -> Subject.failed(sha, failed.message());
            };
        }).toList();
    }

    /** Discovery's stored blobs, without the {@code .part} files it is still writing. */
    private List<Path> blobs() {
        try (Stream<Path> files = Files.list(discoveryDir.resolve("blobs"))) {
            return files.filter(f -> Files.isRegularFile(f) && !String.valueOf(f.getFileName()).endsWith(".part"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String blobText(String sha) {
        Path blob = discoveryDir.resolve("blobs").resolve(sha);
        return Files.exists(blob) ? read(blob) : null;
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

    private static String table(List<Finding> findings, Map<String, List<Location>> locations) {
        StringBuilder csv = new StringBuilder(
                "rule,severity,repository,path,blob_sha,line,message,value_hash,contributing\r\n");
        for (Finding f : findings) {
            for (Location l : locations.getOrDefault(f.blobSha(), List.of())) {
                csv.append(row(f.rule(), f.severity().name(), l.repository(), l.path(), f.blobSha(),
                        f.line() == null ? "" : f.line().toString(), f.message(),
                        f.valueHash() == null ? "" : f.valueHash(), String.join(";", f.contributing())));
            }
        }
        return csv.toString();
    }

    private Map<String, Object> rates(List<Finding> findings, Map<String, List<Location>> locations,
                                      List<String> skipped) {
        Map<String, Map<String, Integer>> rules = new TreeMap<>();
        findings.stream().collect(Collectors.groupingBy(Finding::rule)).forEach((rule, list) -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("findings", list.size());
            counts.put("files", (int) list.stream().map(Finding::blobSha).distinct().count());
            counts.put("locations", list.stream()
                    .mapToInt(f -> locations.getOrDefault(f.blobSha(), List.of()).size()).sum());
            rules.put(rule, counts);
        });
        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("skipped", new TreeSet<>(skipped));
        rates.put("rules", rules);
        return rates;
    }

    /**
     * Up to {@link #SAMPLE} findings per rule, one location each, taken round-robin across organisations (the
     * repository's owner) after a seeded shuffle within each, so the sample is spread and the same on every run.
     */
    private static Map<String, String> samples(List<Finding> findings, Map<String, List<Location>> locations) {
        Map<String, String> samples = new TreeMap<>();
        findings.stream().collect(Collectors.groupingBy(Finding::rule, TreeMap::new, Collectors.toList()))
                .forEach((rule, list) -> samples.put(rule, sample(list, locations)));
        return samples;
    }

    private static String sample(List<Finding> findings, Map<String, List<Location>> locations) {
        Map<String, List<String>> byOrganisation = new TreeMap<>();
        for (Finding f : findings) {
            for (Location l : locations.getOrDefault(f.blobSha(), List.of())) {
                byOrganisation.computeIfAbsent(l.repository().split("/", 2)[0], o -> new ArrayList<>())
                        .add(row(f.rule(), f.severity().name(), l.repository(), l.path(), f.blobSha(),
                                f.line() == null ? "" : f.line().toString(), f.message(), ""));
            }
        }
        Random random = new Random(SEED);
        byOrganisation.values().forEach(rows -> {
            Collections.sort(rows);
            Collections.shuffle(rows, random);
        });
        StringBuilder csv = new StringBuilder("rule,severity,repository,path,blob_sha,line,message,verdict\r\n");
        int longest = byOrganisation.values().stream().mapToInt(List::size).max().orElse(0);
        int taken = 0;
        for (int round = 0; round < longest && taken < SAMPLE; round++) {
            for (List<String> rows : byOrganisation.values()) {
                if (round < rows.size() && taken < SAMPLE) {
                    csv.append(rows.get(round));
                    taken++;
                }
            }
        }
        return csv.toString();
    }

    /** One CSV line (RFC 4180): a field holding a comma, quote or line break is quoted, quotes doubled. */
    private static String row(String... fields) {
        return Stream.of(fields)
                .map(f -> f.matches("(?s).*[,\"\r\n].*") ? "\"" + f.replace("\"", "\"\"") + "\"" : f)
                .collect(Collectors.joining(",", "", "\r\n"));
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
            Files.createDirectories(outDir.resolve("tables"));
            Files.createDirectories(outDir.resolve("samples"));
            Files.writeString(outDir.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
