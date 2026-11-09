package io.github.fiftieshousewife.dockerfileinventory.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SurveyTest {

    private static final Map<String, String> TOKEN = Map.of("GHE_TOKEN_FILE", "/secrets/token");

    @Test
    void aFullRunIsEveryStageButLineageInOrderEachIntoItsOwnFolder() {
        List<Survey.Step> steps = Survey.steps(List.of("https://github.com/acme"), false, TOKEN);

        assertAll(
                () -> assertThat(steps).extracting(Survey.Step::stage)
                        .containsExactly("discovery", "facts", "catalogue", "rules", "validator", "report"),
                () -> assertThat(steps.getFirst().env()).containsEntry("GHE_API_URL", "https://github.com/acme")
                        .containsEntry("OUT_DIR", "out/acme/discovery")
                        .containsEntry("GHE_TOKEN_FILE", "/secrets/token")
                        .doesNotContainKey("TRY_REPOSITORIES"),
                () -> assertThat(steps.get(4).env()).containsEntry("FACTS_DIR", "out/acme/facts")
                        .containsEntry("CATALOGUE_DIR", "out/acme/catalogue")
                        .containsEntry("RULES_DIR", "out/acme/rules")
                        .doesNotContainKey("LINEAGE_DIR"),
                () -> assertThat(steps.getLast().env()).containsEntry("VALIDATOR_DIR", "out/acme/validator")
                        .containsEntry("OUT_DIR", "out/acme/report"));
    }

    @Test
    void aTryStopsDiscoveryAfterTenRepositoriesWithADockerfileAndWritesBesideTheFullRun() {
        Survey.Step discovery = Survey.steps(List.of("https://github.com/acme"), true, TOKEN).getFirst();

        assertThat(discovery.env()).containsEntry("TRY_REPOSITORIES", "10")
                .containsEntry("OUT_DIR", "out/acme-try/discovery");
    }

    @Test
    void withAnArtifactoryLineageRunsAfterTheFactsAndTheLaterStagesReadIt() {
        List<Survey.Step> steps = Survey.steps(List.of("https://github.com/acme"), false,
                Map.of("ARTIFACTORY_URL", "https://artifactory.acme.example/artifactory/"));

        assertAll(
                () -> assertThat(steps).extracting(Survey.Step::stage).containsExactly("discovery", "facts",
                        "lineage", "catalogue", "rules", "validator", "report"),
                () -> assertThat(steps.getLast().env()).containsEntry("LINEAGE_DIR", "out/acme/lineage"));
    }

    @Test
    void anOutputDirectoryGivenHoldsTheStagesFolders() {
        assertThat(Survey.steps(List.of("https://github.com/acme"), false, Map.of("OUT_DIR", "/data")).getFirst().env())
                .containsEntry("OUT_DIR", "/data/discovery");
    }

    @Test
    void theFolderIsTheOrganisationOrElseTheHost() {
        assertAll(
                () -> assertThat(Survey.folder("https://github.com/acme")).isEqualTo("acme"),
                () -> assertThat(Survey.folder("https://ghe.acme.example/platform/")).isEqualTo("platform"),
                () -> assertThat(Survey.folder("https://ghe.acme.example")).isEqualTo("ghe.acme.example"),
                () -> assertThat(Survey.folder("https://acme.ghe.com")).isEqualTo("acme.ghe.com"));
    }

    @Test
    void severalOrganisationsAreEachDiscoveredThenMergedIntoTheOneDiscoveryTheStagesRead() {
        List<Survey.Step> steps = Survey.steps(List.of("https://github.com/acme", "https://github.com/beta"), true,
                TOKEN);

        assertAll(
                () -> assertThat(steps).extracting(Survey.Step::stage).containsExactly("discovery", "discovery",
                        "combine", "facts", "catalogue", "rules", "validator", "report"),
                () -> assertThat(steps.get(1).env()).containsEntry("GHE_API_URL", "https://github.com/beta")
                        .containsEntry("OUT_DIR", "out/acme+beta-try/discovery-of/beta")
                        .containsEntry("TRY_REPOSITORIES", "10"),
                () -> assertThat(steps.get(2).env()).containsEntry("COMBINE_SOURCES",
                        "out/acme+beta-try/discovery-of/acme,out/acme+beta-try/discovery-of/beta")
                        .containsEntry("OUT_DIR", "out/acme+beta-try/discovery"),
                () -> assertThat(steps.get(3).env()).containsEntry("DISCOVERY_DIR", "out/acme+beta-try/discovery"));
    }

    @Test
    void theRootIsTheOrganisationsJoinedMarkedForATryUnlessAnOutputDirectoryIsGiven() {
        List<String> urls = List.of("https://github.com/acme", "https://github.com/beta");

        assertAll(
                () -> assertThat(Survey.root(urls, true, Map.of())).isEqualTo("out/acme+beta-try"),
                () -> assertThat(Survey.root(urls, false, Map.of())).isEqualTo("out/acme+beta"),
                () -> assertThat(Survey.root(urls, true, Map.of("OUT_DIR", "/data"))).isEqualTo("/data"));
    }

    @Test
    void aDiscoveryIsPointedAtItsOrganisationAndStoppedEarlyOnlyForATry() {
        assertAll(
                () -> assertThat(Survey.discovery("https://github.com/acme", false))
                        .isEqualTo(Map.of("GHE_API_URL", "https://github.com/acme")),
                () -> assertThat(Survey.discovery("https://github.com/acme", true))
                        .containsEntry("TRY_REPOSITORIES", "10"));
    }

    @Test
    void aStepWritesIntoItsOwnFolderAndTellsTheLaterStepsWhereThatIs() {
        Map<String, String> dirs = new HashMap<>();

        Survey.Step step = Survey.step(TOKEN, "out/acme", dirs, "facts", Map.of());

        assertAll(
                () -> assertThat(step.env()).containsEntry("OUT_DIR", "out/acme/facts"),
                () -> assertThat(dirs).containsExactly(Map.entry("FACTS_DIR", "out/acme/facts")));
    }

    @Test
    void oneOrganisationIsDiscoveredStraightIntoTheDiscoveryTheStagesRead() {
        Map<String, String> dirs = new HashMap<>();

        assertAll(
                () -> assertThat(Survey.discoveries(List.of("https://github.com/acme"), false, TOKEN, "out/acme",
                        dirs)).extracting(Survey.Step::stage).containsExactly("discovery"),
                () -> assertThat(dirs).containsEntry("DISCOVERY_DIR", "out/acme/discovery"));
    }
}
