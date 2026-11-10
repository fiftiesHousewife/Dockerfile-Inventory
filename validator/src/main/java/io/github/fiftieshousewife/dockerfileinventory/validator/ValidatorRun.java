package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        Map<String, Subject> subjects = byBlob(all);
        List<Subject> images = all.stream().filter(Subject::runnable).toList();
        List<Judged> judged = images.stream()
                .map(image -> new Judged(image.blobSha(), Validation.findings(image, profile))).toList();
        Optional<List<WorkloadVerdict>> workloads = workloads(subjects);
        List<WorkloadVerdict> judgedWorkloads = workloads.orElse(List.of());
        Map<String, Verdict> verdicts = verdicts(judged);
        Map<String, List<Location>> locations = withWorkloads(locations(discoveryDir.resolve("manifest.jsonl")),
                judgedWorkloads);
        List<Action> actions = Actions.of(findings(judged, judgedWorkloads), locations);
        List<Fix> fixes = Fixes.of(actions, catalogue(), subjects, locations);
        write("actions.jsonl", lines(actions));
        write("fixes.jsonl", lines(fixes));
        write("verdicts.jsonl", lines(verdicts.entrySet().stream()
                .map(e -> new ImageVerdict(e.getKey(), e.getValue())).toList()));
        Map<String, Object> summary = ActionPlan.summary(profile, verdicts, all.size() - images.size(), actions,
                fixes.size());
        workloads.ifPresent(list -> {
            write("workload-verdicts.jsonl", lines(list));
            summary.putAll(WorkloadVerdict.summary(list));
        });
        write("action-plan.json", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
    }

    List<Finding> findings(List<Judged> judged, List<WorkloadVerdict> workloads) {
        return Stream.of(judged.stream().flatMap(image -> image.findings().stream()), ruleFindings().stream(),
                workloads.stream().flatMap(workload -> workload.podFindings().stream())).flatMap(s -> s).toList();
    }

    static Map<String, List<Location>> withWorkloads(Map<String, List<Location>> locations,
                                                     List<WorkloadVerdict> workloads) {
        Map<String, List<Location>> all = new LinkedHashMap<>(locations);
        workloads.forEach(workload -> all.putIfAbsent(workload.blobSha(), workload.locations()));
        return all;
    }

    Optional<List<WorkloadVerdict>> workloads(Map<String, Subject> subjects) {
        Path file = factsDir.resolve("workloads.jsonl");
        ImageLinks links = lineageDir == null ? ImageLinks.NONE : ImageLinks.read(lineageDir);
        return Files.isRegularFile(file) ? Optional.of(Workload.read(file).stream()
                .map(workload -> WorkloadVerdict.of(workload, profile, links, subjects)).toList())
                .filter(list -> !list.isEmpty()) : Optional.empty();
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
