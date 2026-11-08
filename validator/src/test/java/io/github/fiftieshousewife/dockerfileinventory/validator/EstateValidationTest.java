package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("validate")
class EstateValidationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theEstatesVerdictsAndActionsPerCheckMatchTheCommittedCounts(@TempDir Path out) throws IOException {
        Path facts = Path.of(System.getenv().getOrDefault("VALIDATE_FACTS_DIR", "../out/all/facts-catalogue"));
        Path discovery = Path.of(System.getenv().getOrDefault("VALIDATE_DISCOVERY_DIR", "../out/all/discovery"));
        assumeTrue(Files.isRegularFile(facts.resolve("facts.jsonl")), "no facts at " + facts.toAbsolutePath());

        new ValidatorRun(facts, discovery, Profile.shipped(Profile.DEFAULT), out).run();

        assertThat(counts(out)).isEqualTo(JSON.readTree(Path.of("src/test/resources/estate/expected.json").toFile()));
    }

    private static JsonNode counts(Path out) throws IOException {
        JsonNode plan = JSON.readTree(out.resolve("action-plan.json").toFile());
        Map<String, Integer> byCheck = new TreeMap<>((a, b) -> Integer.compare(number(a), number(b)));
        Files.readAllLines(out.resolve("actions.jsonl")).stream().map(JSON::readTree)
                .forEach(action -> byCheck.merge(action.path("check").asString(), 1, Integer::sum));
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("images", plan.path("images"));
        counts.put("notRunnable", plan.path("notRunnable"));
        counts.put("verdicts", plan.path("verdicts"));
        counts.put("actionsByCheck", byCheck);
        return JSON.valueToTree(counts);
    }

    private static int number(String check) {
        return Integer.parseInt(check.substring(1));
    }
}
