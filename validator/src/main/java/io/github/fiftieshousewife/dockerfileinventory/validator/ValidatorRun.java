package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

final class ValidatorRun {

    private static final ObjectMapper JSON = new ObjectMapper();

    record ImageVerdict(String blobSha, Verdict verdict) {
    }

    record Judged(String blobSha, List<Finding> findings) {

        Judged {
            findings = List.copyOf(findings);
        }
    }

    private final Path factsDir;
    private final Path discoveryDir;
    private final Path lineageDir;
    private final Path catalogueDir;
    private final Path rulesDir;
    private final Profile profile;
    private final Path outDir;

    ValidatorRun(Path factsDir, Path discoveryDir, Path lineageDir, Path catalogueDir, Path rulesDir, Profile profile,
                 Path outDir) {
        this.factsDir = factsDir;
        this.discoveryDir = discoveryDir;
        this.lineageDir = lineageDir;
        this.catalogueDir = catalogueDir;
        this.rulesDir = rulesDir;
        this.profile = profile;
        this.outDir = outDir;
    }

    void run() {
        List<Subject> all = subjects();
        List<Subject> images = all.stream().filter(Subject::runnable).toList();
        List<Judged> judged = images.stream()
                .map(image -> new Judged(image.blobSha(), Validation.findings(image, profile))).toList();
        List<Finding> findings = Stream.concat(judged.stream().flatMap(image -> image.findings().stream()),
                ruleFindings().stream()).toList();
        Map<String, Verdict> verdicts = verdicts(judged);
        Map<String, List<Location>> locations = locations(discoveryDir.resolve("manifest.jsonl"));
        List<Action> actions = Actions.of(findings, locations);
        List<Fix> fixes = Fixes.of(actions, catalogue(), byBlob(all), locations);
        write("actions.jsonl", lines(actions));
        write("fixes.jsonl", lines(fixes));
        write("verdicts.jsonl", lines(verdicts.entrySet().stream()
                .map(e -> new ImageVerdict(e.getKey(), e.getValue())).toList()));
        write("action-plan.json", JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(summary(verdicts, all.size() - images.size(), actions, fixes.size())));
    }

    List<Subject> subjects() {
        Map<String, EffectiveUser> users = lineageDir == null ? Map.of() : EffectiveUsers.read(lineageDir);
        return Subject.read(factsDir.resolve("facts.jsonl")).stream()
                .map(subject -> subject.withEffectiveUser(users.get(subject.blobSha()))).toList();
    }

    List<Finding> ruleFindings() {
        return rulesDir == null ? List.of() : RuleFindings.read(rulesDir, profile);
    }

    Catalogue catalogue() {
        return catalogueDir == null ? Catalogue.NONE : Catalogue.read(catalogueDir);
    }

    static Map<String, Verdict> verdicts(List<Judged> judged) {
        return judged.stream().collect(Collectors.toMap(Judged::blobSha, image -> Verdict.of(image.findings()),
                (first, last) -> last, LinkedHashMap::new));
    }

    static Map<String, Subject> byBlob(List<Subject> subjects) {
        return subjects.stream().collect(Collectors.toMap(Subject::blobSha, s -> s, (a, b) -> a));
    }

    Map<String, Object> summary(Map<String, Verdict> verdicts, int notRunnable, List<Action> actions, int fixes) {
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
        summary.put("fixes", fixes);
        summary.put("repositories", Actions.byRepository(actions).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().size(), (a, b) -> a, LinkedHashMap::new)));
        return summary;
    }

    static Map<String, List<Location>> locations(Path manifest) {
        return JsonLines.read(manifest).stream().collect(Collectors.groupingBy(
                row -> row.path("blobSha").asString(), LinkedHashMap::new, Collectors.mapping(
                        row -> new Location(row.path("repository").asString(), row.path("path").asString()),
                        Collectors.toList())));
    }

    static String lines(List<?> rows) {
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
