package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ActionPlanTest {

    @Test
    void verdictsAreCountedLowerCaseWorstFirst() {
        assertThat(ActionPlan.counts(Stream.of(Verdict.RUNS, Verdict.REFUSED, Verdict.RUNS)).toString())
                .isEqualTo("{refused=1, runs=2}");
    }

    @Test
    void theSummaryNamesTheProfileAndCountsTheImagesActionsAndFixes() {
        Map<String, Object> summary = ActionPlan.summary(Fixtures.V2, Map.of("a", Verdict.RUNS), 2, List.of(), 0);

        assertAll(
                () -> assertThat(summary).containsEntry("profile", "openshift-4-restricted-v2"),
                () -> assertThat(summary).containsEntry("images", 1).containsEntry("notRunnable", 2)
                        .containsEntry("actions", 0).containsEntry("fixes", 0),
                () -> assertThat(summary.get("repositories")).isEqualTo(Map.of()));
    }
}
