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
    private static final String UNSET = "00b7e9b0f4f07bea7f89b52371b0221dc848d085";
    private static final String ROOT = "007a290c65b529f0a12b9b03b2e67ab5df5cfefd";

    @TempDir
    Path out;

    private JsonNode plan() {
        new ValidatorRun(FIXTURES, FIXTURES, null, null, null, Fixtures.V2, out).run();
        return JSON.readTree(out.resolve("action-plan.json").toFile());
    }

    @Test
    void thePlanSummarisesTheProfileTheVerdictsAndTheActionsPerRepository() {
        JsonNode plan = plan();

        assertAll(
                () -> assertThat(plan.path("profile").asString()).isEqualTo("openshift-4-restricted-v2"),
                () -> assertThat(plan.path("images").asInt()).isEqualTo(14),
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

        assertThat(Files.readAllLines(out.resolve("verdicts.jsonl"))).hasSize(14)
                .contains("{\"blobSha\":\"01c982921d4919768f2c03ea30ab8db209baab7a\",\"verdict\":\"RUNS\"}");
    }

    @Test
    void withLineageAnImageSettingNoUserWhoseRegistryImagesRunAsANumericUserHasNoUserFinding(@TempDir Path lineage)
            throws IOException {
        Files.writeString(lineage.resolve("links.jsonl"), "{\"configDigest\":\"sha256:app\",\"blobShas\":"
                + "[\"00b7e9b0f4f07bea7f89b52371b0221dc848d085\"],\"credited\":true}\n");
        Files.writeString(lineage.resolve("effective.jsonl"),
                "{\"configDigest\":\"sha256:app\",\"user\":{\"value\":\"1001\",\"setBy\":{\"kind\":\"Unknown\"}}}\n");

        new ValidatorRun(FIXTURES, FIXTURES, lineage, null, null, Fixtures.V2, out).run();

        assertThat(Files.readAllLines(out.resolve("actions.jsonl")).stream().map(JSON::readTree)
                .filter(action -> action.path("check").asString().equals("P1"))
                .map(action -> action.path("blobSha").asString()))
                .doesNotContain("00b7e9b0f4f07bea7f89b52371b0221dc848d085");
    }

    @Test
    void withoutACatalogueEachActionIsAFixOfItsOwnAndThePlanCountsThem() throws IOException {
        JsonNode plan = plan();

        assertAll(
                () -> assertThat(Files.readAllLines(out.resolve("fixes.jsonl")))
                        .hasSameSizeAs(Files.readAllLines(out.resolve("actions.jsonl"))),
                () -> assertThat(plan.path("fixes").asInt()).isEqualTo(plan.path("actions").asInt()));
    }

    @Test
    void withACatalogueTheSameCheckOnAnArchetypesCopiesIsOneFix(@TempDir Path catalogue) throws IOException {
        Files.writeString(catalogue.resolve("archetypes.jsonl"), "{\"key\":\"k\",\"representative\":\"" + UNSET
                + "\",\"blobShas\":[\"" + UNSET + "\",\"" + ROOT + "\"]}\n");
        Files.createDirectories(catalogue.resolve("catalogue"));
        Files.writeString(catalogue.resolve("catalogue/dockerfiles.jsonl"), "");

        new ValidatorRun(FIXTURES, FIXTURES, null, catalogue, null, Fixtures.V2, out).run();

        assertThat(Files.readAllLines(out.resolve("fixes.jsonl")))
                .anyMatch(line -> line.contains("\"copies\":[\"" + ROOT + "\",\"" + UNSET + "\"]"));
    }

    @Test
    void withTheRulesTheirFindingsAreActionsButNotVerdicts(@TempDir Path rules) throws IOException {
        plan();
        List<String> verdictsWithout = Files.readAllLines(out.resolve("verdicts.jsonl"));
        Files.writeString(rules.resolve("findings.jsonl"), "{\"rule\":\"S2\",\"severity\":\"CRITICAL\",\"blobSha\":\""
                + UNSET + "\",\"message\":\"ENV TOKEN holds a secret\"}\n");

        new ValidatorRun(FIXTURES, FIXTURES, null, null, rules, Fixtures.V2, out).run();

        assertAll(
                () -> assertThat(Files.readAllLines(out.resolve("actions.jsonl"))).anyMatch(line ->
                        line.contains("\"check\":\"S2\"") && line.contains(UNSET)),
                () -> assertThat(Files.readAllLines(out.resolve("verdicts.jsonl"))).isEqualTo(verdictsWithout));
    }
}
