package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainerImageTest {

    private static Workload.Container probed(boolean probed) {
        return new Workload.Container("c", "image:1", false, List.of(), List.of(),
                new Workload.Probes(probed, false, false), List.of(), WorkloadTest.NOTHING);
    }

    @Test
    void anImagesPlatformFindingsAreKeptAndItsP9IsTheOneTheContainerLeavesUnanswered() {
        assertThat(ContainerImage.findings(subject("2fc5b392"), probed(true), V2)).extracting(Finding::check)
                .isEqualTo(Validation.findings(subject("2fc5b392"), V2).stream().map(Finding::check)
                        .filter(check -> check != Check.P9).toList());
    }

    @Test
    void anUnansweredHealthcheckStaysAFindingOnTheImage() {
        assertThat(ContainerImage.findings(subject("2fc5b392"), probed(false), V2))
                .filteredOn(finding -> finding.check() == Check.P9).extracting(Finding::blobSha)
                .containsExactly("2fc5b392e3ec9072112324117c989bdee6934113");
    }

    @Test
    void aP9TheProfileTurnsOffFindsNothing() {
        Map<Check, Severity> severity = new EnumMap<>(V2.severity());
        severity.put(Check.P9, Severity.OFF);
        Profile off = new Profile("x", "", List.of(), severity, List.of());

        assertThat(ContainerImage.findings(subject("2fc5b392"), probed(false), off))
                .noneMatch(finding -> finding.check() == Check.P9);
    }
}
