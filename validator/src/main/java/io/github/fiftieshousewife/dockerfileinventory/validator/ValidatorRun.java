package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

// Reads the facts, the manifest and lineage's effective users when given, judges every image through the profile, writes the plan and its actions.
final class ValidatorRun {

    private static final ObjectMapper JSON = new ObjectMapper();

    record ImageVerdict(String blobSha, Verdict verdict) {
    }

    private final Path factsDir;
    private final Path discoveryDir;
    private final Path lineageDir;
    private final Profile profile;
    private final Path outDir;

    ValidatorRun(Path factsDir, Path discoveryDir, Path lineageDir, Profile profile, Path outDir) {
        this.factsDir = factsDir;
        this.discoveryDir = discoveryDir;
        this.lineageDir = lineageDir;
        this.profile = profile;
        this.outDir = outDir;
    }

    void run() {
        Map<String, EffectiveUser> users = lineageDir == null ? Map.of() : EffectiveUsers.read(lineageDir);
        List<Subject> all = Subject.read(factsDir.resolve("facts.jsonl")).stream()
                .map(subject -> subject.withEffectiveUser(users.get(subject.blobSha()))).toList();
        List<Subject> images = all.stream().filter(Subject::runnable).toList();
        List<Finding> findings = new ArrayList<>();
        Map<String, Verdict> verdicts = new LinkedHashMap<>();
        images.forEach(image -> {
            List<Finding> own = Validation.findings(image, profile);
            findings.addAll(own);
            verdicts.put(image.blobSha(), Verdict.of(own));
        });
        List<Action> actions = Actions.of(findings, locations(discoveryDir.resolve("manifest.jsonl")));
        write("actions.jsonl", lines(actions));
        write("verdicts.jsonl", lines(verdicts.entrySet().stream()
                .map(e -> new ImageVerdict(e.getKey(), e.getValue())).toList()));
        write("action-plan.json", JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(summary(verdicts, all.size() - images.size(), actions)));
    }

    Map<String, Object> summary(Map<String, Verdict> verdicts, int notRunnable, List<Action> actions) {
        Map<Verdict, Long> counted = new EnumMap<>(Verdict.class);
        verdicts.values().forEach(v -> counted.merge(v, 1L, Long::sum));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("profile", profile.name());
        summary.put("describes", profile.describes());
        summary.put("images", verdicts.size());
        summary.put("notRunnable", notRunnable);
        summary.put("verdicts", counted.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().name().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a,
                LinkedHashMap::new)));
        summary.put("actions", actions.size());
        summary.put("repositories", Actions.byRepository(actions).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().size(), (a, b) -> a, LinkedHashMap::new)));
        return summary;
    }

    static Map<String, List<Location>> locations(Path manifest) {
        try (var lines = Files.lines(manifest)) {
            return lines.filter(line -> !line.isBlank()).map(JSON::readTree).collect(Collectors.groupingBy(
                    row -> row.path("blobSha").asString(), LinkedHashMap::new, Collectors.mapping(
                            row -> new Location(row.path("repository").asString(), row.path("path").asString()),
                            Collectors.toList())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lines(List<?> rows) {
        return rows.stream().map(JSON::writeValueAsString).collect(Collectors.joining("\n", "", rows.isEmpty() ? ""
                : "\n"));
    }

    private void write(String file, String text) {
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
