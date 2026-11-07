package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigInteger;
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

    @Test
    void fnv1aMatchesThePublishedSixtyFourBitVectors() {
        assertThat(MinHash.fnv1a("")).isEqualTo(0xcbf29ce484222325L);
        assertThat(MinHash.fnv1a("a")).isEqualTo(0xaf63dc4c8601ec8cL);
    }

    @Test
    void mulAddModAgreesWithExactArithmeticModuloTheMersennePrime() {
        BigInteger prime = BigInteger.ONE.shiftLeft(61).subtract(BigInteger.ONE);
        long a = (1L << 60) + 12345;
        long x = (1L << 61) - 2;
        long b = 987654321L;

        assertThat(MinHash.mulAddMod(2, 3, 4)).isEqualTo(10);
        assertThat(MinHash.mulAddMod(a, x, b)).isEqualTo(BigInteger.valueOf(a).multiply(BigInteger.valueOf(x))
                .add(BigInteger.valueOf(b)).mod(prime).longValueExact());
    }

    @Test
    void theJaccardOfTwoEmptySetsIsZero() {
        assertThat(MinHash.jaccard(Set.of(), Set.of())).isZero();
    }

    private static Set<String> range(int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> "pkg-" + i).collect(Collectors.toSet());
    }
}
