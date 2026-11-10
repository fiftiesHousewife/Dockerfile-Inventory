package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

record ActionPlanView(Plan plan, List<PlannedAction> actions, List<PlannedFix> fixes,
                      List<PlannedWorkload> workloads) {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    record Plan(String profile, String describes, int images, int notRunnable, Map<String, Integer> verdicts,
                int actions, int fixes, Map<String, Integer> repositories, Integer workloads,
                Map<String, Integer> workloadVerdicts) {
    }

    record PlannedAction(String check, String severity, String certainty, String what, String blobSha,
                         List<String> because, List<Where> where) {
    }

    record Where(String repository, String path) {
    }

    record PlannedFix(String check, String severity, String certainty, String what, String representative,
                      List<String> because, List<String> copies, List<String> inheriting, int locations) {
    }

    record PlannedWorkload(String blobSha, String kind, String name, String namespace, List<Where> locations,
                           String verdict, List<PlannedContainer> containers, List<PlannedFinding> findings) {
    }

    record PlannedContainer(String name, String image, List<String> dockerfiles, boolean floating,
                            String unlinked) {
    }

    record PlannedFinding(String check, String severity, String blobSha, String message) {
    }

    ActionPlanView {
        actions = List.copyOf(actions);
        fixes = List.copyOf(fixes);
        workloads = List.copyOf(workloads);
    }

    static ActionPlanView read(Path validatorDir) {
        return new ActionPlanView(JSON.readValue(validatorDir.resolve("action-plan.json").toFile(), Plan.class),
                rows(validatorDir.resolve("actions.jsonl"), PlannedAction.class),
                rows(validatorDir.resolve("fixes.jsonl"), PlannedFix.class), workloads(validatorDir));
    }

    static List<PlannedWorkload> workloads(Path validatorDir) {
        Path file = validatorDir.resolve("workload-verdicts.jsonl");
        return Files.isRegularFile(file) ? rows(file, PlannedWorkload.class) : List.of();
    }

    static <T> List<T> rows(Path file, Class<T> type) {
        try (var lines = Files.lines(file)) {
            return lines.filter(line -> !line.isBlank()).map(line -> JSON.readValue(line, type)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
