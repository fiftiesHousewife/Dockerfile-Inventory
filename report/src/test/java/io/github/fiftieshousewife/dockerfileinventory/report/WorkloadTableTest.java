package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkloadTableTest {

    private static final List<ActionPlanView.PlannedWorkload> REAL = ActionPlanPageTest.WORKLOADS.workloads();

    private static ActionPlanView.PlannedWorkload linked(String verdict, String name, boolean floating) {
        return new ActionPlanView.PlannedWorkload("yaml", "Deployment", name, "shop",
                List.of(new ActionPlanView.Where("team/shop", "deploy/app.yaml")), verdict,
                List.of(new ActionPlanView.PlannedContainer("app", "quay.io/team/app:1.2", List.of("abc12345ffff"),
                        floating, null)),
                List.of(new ActionPlanView.PlannedFinding("P1", "BREAKS", "abc12345ffff", "runs as root")));
    }

    @Test
    void aLinkedContainerNamesItsImageAndLinksItsCataloguedDockerfile() {
        assertThat(WorkloadTable.containers(linked("BREAKS", "app", false), Set.of("abc12345ffff")))
                .isEqualTo("app: quay.io/team/app:1.2, built from <a href=\"dockerfiles/abc12345ffff.html\">"
                        + "abc12345</a>");
    }

    @Test
    void aFloatingContainerIsFlaggedAndAnUncataloguedDockerfileIsNotLinked() {
        assertThat(WorkloadTable.containers(linked("BREAKS", "app", true), Set.of()))
                .isEqualTo("app: quay.io/team/app:1.2 (floats), built from abc12345");
    }

    @Test
    void anUnlinkedContainerSaysWhy() {
        assertThat(WorkloadTable.containers(REAL.getFirst(), Set.of()))
                .isEqualTo("sleep-test-container: rhel7, unlinked: no lineage");
    }

    @Test
    void aContainerNamingNoImageSaysSo() {
        assertThat(WorkloadTable.image(new ActionPlanView.PlannedContainer("sync", " ", List.of(), false, "x")))
                .isEqualTo("no image");
    }

    @Test
    void eachRowGivesTheVerdictTheWorkloadItsFileAndItsFindings() {
        assertThat(WorkloadTable.row(linked("BREAKS", "app", false), Set.of())).contains("<td>breaks</td>",
                "<td>Deployment app (shop)</td>", "<td>team/shop deploy/app.yaml</td>", "<td>P1: runs as root</td>");
    }

    @Test
    void theWorkloadsAreShownWorstFirstThenByName() {
        String table = WorkloadTable.render(List.of(linked("RUNS", "a", false), linked("REFUSED", "z", false),
                linked("REFUSED", "b", false)), Set.of());

        assertThat(List.of(table.indexOf("Deployment b "), table.indexOf("Deployment z "),
                table.indexOf("Deployment a "))).isSorted();
    }

    @Test
    void onlyTheFirstWorkloadsAreShownAndTheRestCounted() {
        assertThat(WorkloadTable.render(Collections.nCopies(ActionPlanPage.SHOWN + 2, linked("RUNS", "a", false)),
                Set.of())).contains("and 2 more in the validator's workload-verdicts.jsonl");
    }

    @Test
    void withoutWorkloadsThereIsNoTable() {
        assertThat(WorkloadTable.render(List.of(), Set.of())).isEmpty();
    }
}
