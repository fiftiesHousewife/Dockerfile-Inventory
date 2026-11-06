package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MinHashTest {

    @Test
    void equalSetsHaveEqualSignatures() {
        assertThat(MinHash.signature(Set.of("git", "tar", "base:ubi-minimal")))
                .isEqualTo(MinHash.signature(Set.of("base:ubi-minimal", "tar", "git")))
                .isNotEqualTo(MinHash.signature(Set.of("git", "tar", "base:alpine")));
    }

    @Test
    void theEstimateIsWithinATenthOfTheExactJaccard() {
        Set<String> a = range(0, 100);
        Set<String> half = range(50, 150);
        Set<String> most = range(10, 110);

        assertThat(MinHash.jaccard(a, half)).isCloseTo(50.0 / 150, within(1e-9));
        assertThat(MinHash.jaccard(a, most)).isCloseTo(90.0 / 110, within(1e-9));
        assertThat(MinHash.estimate(MinHash.signature(a), MinHash.signature(half)))
                .isCloseTo(MinHash.jaccard(a, half), within(0.1));
        assertThat(MinHash.estimate(MinHash.signature(a), MinHash.signature(most)))
                .isCloseTo(MinHash.jaccard(a, most), within(0.1));
    }

    private static Set<String> range(int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> "pkg-" + i).collect(Collectors.toSet());
    }
}
