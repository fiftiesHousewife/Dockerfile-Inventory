package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class CoverageTest {

    @Test
    void coveringIsTheFewestLeadingCountsReachingThePercentAndAllOfThemOtherwise() {
        List<Integer> sorted = List.of(50, 30, 20);

        assertAll(
                () -> assertThat(Coverage.fewestCovering(sorted, 100, 80)).isEqualTo(2),
                () -> assertThat(Coverage.fewestCovering(sorted, 100, 50)).isEqualTo(1),
                () -> assertThat(Coverage.fewestCovering(sorted, 200, 95)).isEqualTo(3));
    }

    @Test
    void withoutCountsNothingIsCovered() {
        assertThat(Coverage.fewestCovering(List.of(), 0, 80)).isZero();
    }
}
