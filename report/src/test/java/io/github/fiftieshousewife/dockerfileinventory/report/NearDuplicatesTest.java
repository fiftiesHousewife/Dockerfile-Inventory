package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NearDuplicatesTest {

    @Test
    void groupsAtJaccardPointEightOrMoreMergeAndLessSimilarOnesDoNot() {
        Group a = group("a", 5, packages(0, 9));
        Group close = group("close", 3, packages(1, 10));
        Group far = group("far", 2, packages(4, 13));

        assertThat(NearDuplicates.of(List.of(a, close, far)))
                .extracting(Group::blobShas)
                .containsExactlyInAnyOrder(List.of("a", "close"), List.of("far"));
    }

    @Test
    void mergingIsTransitiveAndTheLargestMemberNamesTheGroup() {
        Group a = group("a", 1, packages(0, 9));
        Group b = group("b", 7, packages(1, 10));
        Group c = group("c", 2, packages(2, 11));

        assertThat(MinHash.jaccard(features(a), features(c))).isLessThan(NearDuplicates.THRESHOLD);
        assertThat(NearDuplicates.of(List.of(a, b, c))).singleElement().satisfies(merged -> {
            assertThat(merged.key()).isEqualTo("b");
            assertThat(merged.blobShas()).containsExactly("a", "b", "c");
            assertThat(merged.locations()).isEqualTo(10);
        });
    }

    @Test
    void groupsWithNoPackagesNeverMergeHere() {
        assertThat(NearDuplicates.of(List.of(group("x", 1, Set.of()), group("y", 1, Set.of())))).hasSize(2);
    }

    @Test
    void theResultDoesNotDependOnInputOrder() {
        List<Group> groups = List.of(group("a", 5, packages(0, 9)), group("close", 3, packages(1, 10)),
                group("far", 2, packages(4, 13)), group("empty", 1, Set.of()));

        assertThat(NearDuplicates.of(groups.reversed())).isEqualTo(NearDuplicates.of(groups));
    }

    private static Set<String> features(Group g) {
        Set<String> features = new TreeSet<>(g.packages());
        features.add("base:" + g.family());
        return features;
    }

    static Group group(String key, int locations, Set<String> packages) {
        return new Group(key, List.of(key), locations, packages, "ubi-minimal");
    }

    static Set<String> packages(int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> "pkg-" + i).collect(Collectors.toCollection(TreeSet::new));
    }
}
