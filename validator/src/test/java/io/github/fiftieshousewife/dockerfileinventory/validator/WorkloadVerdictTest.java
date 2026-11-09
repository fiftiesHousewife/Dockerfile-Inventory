package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.REAL;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkloadVerdictTest {

    @Test
    void aWorkloadTakesTheWorstOfItsPodFindings() {
        WorkloadVerdict sync = WorkloadVerdict.of(real("d88374c0"), V2);

        assertAll(
                () -> assertThat(sync.verdict()).isEqualTo(Verdict.REFUSED),
                () -> assertThat(sync.name()).isEqualTo("sync"),
                () -> assertThat(sync.findings()).isNotEmpty());
    }

    @Test
    void aWorkloadAskingNothingOfTheHostRuns() {
        assertThat(WorkloadVerdict.of(real("a185638a"), V2).verdict()).isEqualTo(Verdict.RUNS);
    }

    @Test
    void theSummaryCountsTheWorkloadsAndEachVerdictWorstFirst() {
        Map<String, Object> summary = WorkloadVerdict.summary(REAL.stream()
                .map(workload -> WorkloadVerdict.of(workload, V2)).toList());

        assertAll(
                () -> assertThat(summary).containsEntry("workloads", 3),
                () -> assertThat(summary.get("workloadVerdicts")).isEqualTo(Map.of("refused", 2L, "runs", 1L)),
                () -> assertThat(summary.get("workloadVerdicts").toString()).isEqualTo("{refused=2, runs=1}"));
    }
}
