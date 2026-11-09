package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    void eachWorkloadIsWrittenWithItsVerdictAndFindings() throws IOException {
        plan();
        List<JsonNode> workloads = Files.readAllLines(out.resolve("workload-verdicts.jsonl")).stream()
                .map(JSON::readTree).toList();
        JsonNode sync = workloads.stream().filter(w -> w.path("name").asString().equals("sync")).findFirst()
                .orElseThrow();

        assertAll(
                () -> assertThat(workloads).hasSize(3),
                () -> assertThat(sync.path("verdict").asString()).isEqualTo("REFUSED"),
                () -> assertThat(sync.path("kind").asString()).isEqualTo("DaemonSet"),
                () -> assertThat(sync.path("locations").get(0).path("repository").asString())
                        .isEqualTo("openshift/openshift-tools"),
                () -> assertThat(sync.path("findings").valueStream().map(f -> f.path("check").asString()))
                        .contains("D1", "D2", "D5"));
    }

    @Test
    void thePlanCountsTheWorkloadsAndTheirVerdicts() {
        JsonNode plan = plan();

        assertAll(
                () -> assertThat(plan.path("workloads").asInt()).isEqualTo(3),
                () -> assertThat(plan.path("workloadVerdicts").path("refused").asInt()).isEqualTo(2),
                () -> assertThat(plan.path("workloadVerdicts").path("runs").asInt()).isEqualTo(1));
    }

    @Test
    void anEmptyWorkloadFileLeavesThePlanAsWithoutOne(@TempDir Path facts) throws IOException {
        Files.copy(FIXTURES.resolve("facts.jsonl"), facts.resolve("facts.jsonl"));
        Files.writeString(facts.resolve("workloads.jsonl"), "");
        new ValidatorRun(facts, FIXTURES, null, null, null, Fixtures.V2, out).run();

        assertAll(
                () -> assertThat(JSON.readTree(out.resolve("action-plan.json").toFile()).has("workloads")).isFalse(),
                () -> assertThat(out.resolve("workload-verdicts.jsonl")).doesNotExist());
    }

    @Test
    void withoutWorkloadsThePlanHasNoWorkloadCountsAndNoWorkloadFile(@TempDir Path facts) throws IOException {
        Files.copy(FIXTURES.resolve("facts.jsonl"), facts.resolve("facts.jsonl"));
        new ValidatorRun(facts, FIXTURES, null, null, null, Fixtures.V2, out).run();

        assertAll(
                () -> assertThat(JSON.readTree(out.resolve("action-plan.json").toFile()).has("workloads")).isFalse(),
                () -> assertThat(out.resolve("workload-verdicts.jsonl")).doesNotExist());
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

    private static Finding finding(String blobSha, Severity severity) {
        return new Finding(Check.P1, severity, Certainty.READ, blobSha, "m");
    }

    @Test
    void eachImageJudgedTwiceKeepsItsPlaceAndItsLastVerdict() {
        assertThat(ValidatorRun.verdicts(List.of(new ValidatorRun.Judged("a", List.of(finding("a", Severity.BREAKS))),
                new ValidatorRun.Judged("b", List.of()), new ValidatorRun.Judged("a", List.of()))))
                .containsExactly(Map.entry("a", Verdict.RUNS), Map.entry("b", Verdict.RUNS));
    }

    @Test
    void rowsAreWrittenOnePerLineEachEndedAndNoneIsEmpty() {
        assertAll(
                () -> assertThat(ValidatorRun.lines(List.of("a", "b"))).isEqualTo("\"a\"\n\"b\"\n"),
                () -> assertThat(ValidatorRun.lines(List.of())).isEmpty());
    }

    @Test
    void withoutTheRulesOrTheCatalogueTheirPartsAreEmpty() {
        ValidatorRun run = new ValidatorRun(FIXTURES, FIXTURES, null, null, null, Fixtures.V2, out);

        assertAll(
                () -> assertThat(run.ruleFindings()).isEmpty(),
                () -> assertThat(run.catalogue()).isEqualTo(Catalogue.NONE));
    }

    @Test
    void withoutLineageEachSubjectHasNoEffectiveUser() {
        assertThat(new ValidatorRun(FIXTURES, FIXTURES, null, null, null, Fixtures.V2, out).subjects())
                .extracting(Subject::effectiveUser).containsOnlyNulls();
    }

    @Test
    void subjectsByBlobKeepTheFirstOfAnyTwoWithTheSameBlob() {
        Subject first = new Subject("a", null, null, List.of(), List.of(new Subject.Step("RUN", "one")));

        assertThat(ValidatorRun.byBlob(List.of(first, new Subject("a", null, null, List.of(), List.of()))))
                .containsExactly(Map.entry("a", first));
    }
}
