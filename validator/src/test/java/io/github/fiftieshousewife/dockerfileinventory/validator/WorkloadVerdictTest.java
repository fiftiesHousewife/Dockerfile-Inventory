package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.REAL;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.named;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
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
    void aWorkloadAskingNothingOfTheHostButNamingAFloatingImageBreaks() {
        assertThat(WorkloadVerdict.of(real("a185638a"), V2).verdict()).isEqualTo(Verdict.BREAKS);
    }

    @Test
    void theSummaryCountsTheWorkloadsAndEachVerdictWorstFirst() {
        Map<String, Object> summary = WorkloadVerdict.summary(REAL.stream()
                .map(workload -> WorkloadVerdict.of(workload, V2)).toList());

        assertAll(
                () -> assertThat(summary).containsEntry("workloads", 3),
                () -> assertThat(summary.get("workloadVerdicts")).isEqualTo(Map.of("refused", 2L, "breaks", 1L)),
                () -> assertThat(summary.get("workloadVerdicts").toString()).isEqualTo("{refused=2, breaks=1}"));
    }

    private static final Map<String, Subject> SUBJECTS = ValidatorRun.byBlob(Subject.read(Fixtures.FACTS));

    @Test
    void eachContainerIsListedWithItsLink() {
        WorkloadVerdict inspector = WorkloadVerdict.of(real("fe15041e"), V2, ImageLinksTest.LINKS, SUBJECTS);

        assertThat(inspector.containers()).extracting(ContainerLink::dockerfiles, ContainerLink::floating)
                .containsExactly(tuple(List.of("2fc5b392e3ec9072112324117c989bdee6934113"), true));
    }

    @Test
    void aLinkedWorkloadTakesItsImagesFindingsLessThoseTheManifestAnswers() {
        Workload postgres = pod(NOTHING, named("db", "team/postgres:9.6", NOTHING));
        Subject image = SUBJECTS.get("141f718e54c8b80c550646a4ea3d0bff996df176");
        WorkloadVerdict verdict = WorkloadVerdict.of(postgres, V2, ImageLinksTest.LINKS, SUBJECTS);

        assertAll(
                () -> assertThat(verdict.findings())
                        .isEqualTo(ContainerImage.findings(image, postgres.pod().containers().getFirst(), V2)),
                () -> assertThat(verdict.verdict()).isEqualTo(Verdict.of(Validation.findings(image, V2))));
    }

    @Test
    void aDockerfileTheFactsDoNotHoldAddsNothing() {
        Workload postgres = pod(NOTHING, named("db", "team/postgres:9.6", NOTHING));

        assertThat(WorkloadVerdict.of(postgres, V2, ImageLinksTest.LINKS, Map.of()).findings()).isEmpty();
    }

    @Test
    void thePodFindingsAreThoseOfTheDChecks() {
        WorkloadVerdict inspector = WorkloadVerdict.of(real("fe15041e"), V2, ImageLinksTest.LINKS, SUBJECTS);

        assertThat(inspector.podFindings()).extracting(Finding::check).containsExactly(Check.D1, Check.D5, Check.D7);
    }
}
