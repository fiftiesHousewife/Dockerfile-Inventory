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

// The validator's output as the report reads it: action-plan.json, actions.jsonl and fixes.jsonl.
record ActionPlanView(Plan plan, List<PlannedAction> actions, List<PlannedFix> fixes) {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    record Plan(String profile, String describes, int images, int notRunnable, Map<String, Integer> verdicts,
                int actions, int fixes, Map<String, Integer> repositories) {
    }

    record PlannedAction(String check, String severity, String certainty, String what, String blobSha,
                         List<String> because, List<Where> where) {
    }

    record Where(String repository, String path) {
    }

    record PlannedFix(String check, String severity, String certainty, String what, String representative,
                      List<String> because, List<String> copies, List<String> inheriting, int locations) {
    }

    ActionPlanView {
        actions = List.copyOf(actions);
        fixes = List.copyOf(fixes);
    }

    static ActionPlanView read(Path validatorDir) {
        return new ActionPlanView(JSON.readValue(validatorDir.resolve("action-plan.json").toFile(), Plan.class),
                rows(validatorDir.resolve("actions.jsonl"), PlannedAction.class),
                rows(validatorDir.resolve("fixes.jsonl"), PlannedFix.class));
    }

    static <T> List<T> rows(Path file, Class<T> type) {
        try (var lines = Files.lines(file)) {
            return lines.filter(line -> !line.isBlank()).map(line -> JSON.readValue(line, type)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
