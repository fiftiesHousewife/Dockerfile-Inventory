package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ValidatorRunTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path out;

    private JsonNode plan() {
        new ValidatorRun(FIXTURES, FIXTURES, Fixtures.V2, out).run();
        return JSON.readTree(out.resolve("action-plan.json").toFile());
    }

    @Test
    void thePlanSummarisesTheProfileTheVerdictsAndTheActionsPerRepository() {
        JsonNode plan = plan();

        assertAll(
                () -> assertThat(plan.path("profile").asString()).isEqualTo("openshift-4-restricted-v2"),
                () -> assertThat(plan.path("images").asInt()).isEqualTo(13),
                () -> assertThat(plan.path("notRunnable").asInt()).isEqualTo(1),
                () -> assertThat(plan.path("verdicts").path("runs").asInt()).isEqualTo(1),
                () -> assertThat(plan.path("verdicts").path("drift").asInt()).isEqualTo(2),
                () -> assertThat(plan.path("repositories").path("openshift/moby-moby").asInt()).isPositive());
    }

    @Test
    void theActionsAreWrittenOnePerLineMostUrgentFirst() throws IOException {
        plan();
        List<JsonNode> actions = Files.readAllLines(out.resolve("actions.jsonl")).stream().map(JSON::readTree)
                .toList();

        assertAll(
                () -> assertThat(actions.getFirst().path("severity").asString()).isEqualTo("BREAKS"),
                () -> assertThat(actions.getLast().path("severity").asString()).isEqualTo("INFORMATION"),
                () -> assertThat(actions.getFirst().path("where").get(0).path("repository").asString()).isNotEmpty());
    }

    @Test
    void eachImagesVerdictIsWrittenOnePerLine() throws IOException {
        plan();

        assertThat(Files.readAllLines(out.resolve("verdicts.jsonl"))).hasSize(13)
                .contains("{\"blobSha\":\"01c982921d4919768f2c03ea30ab8db209baab7a\",\"verdict\":\"RUNS\"}");
    }
}
