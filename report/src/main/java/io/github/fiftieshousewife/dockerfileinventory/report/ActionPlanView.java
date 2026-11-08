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

// The validator's output as the report reads it: action-plan.json and actions.jsonl.
record ActionPlanView(Plan plan, List<PlannedAction> actions) {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    record Plan(String profile, String describes, int images, int notRunnable, Map<String, Integer> verdicts,
                int actions, Map<String, Integer> repositories) {
    }

    record PlannedAction(String check, String severity, String certainty, String what, String blobSha,
                         List<String> because, List<Where> where) {
    }

    record Where(String repository, String path) {
    }

    ActionPlanView {
        actions = List.copyOf(actions);
    }

    static ActionPlanView read(Path validatorDir) {
        try (var lines = Files.lines(validatorDir.resolve("actions.jsonl"))) {
            return new ActionPlanView(JSON.readValue(validatorDir.resolve("action-plan.json").toFile(), Plan.class),
                    lines.filter(line -> !line.isBlank()).map(line -> JSON.readValue(line, PlannedAction.class))
                            .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
