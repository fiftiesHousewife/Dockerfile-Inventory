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

    @Test
    void aGroupsFeaturesAreItsPackagesAndItsBaseFamily() {
        assertThat(NearDuplicates.features(group("a", 1, Set.of("git", "tar"))))
                .containsExactly("base:ubi-minimal", "git", "tar");
    }

    @Test
    void groupsWithPackagesFallIntoOneBucketPerBandAndEmptyOnesIntoNone() {
        List<Group> groups = List.of(group("a", 1, packages(0, 5)), group("b", 1, Set.of()));

        assertThat(NearDuplicates.buckets(groups, groups.stream().map(NearDuplicates::features).toList()))
                .hasSize(NearDuplicates.BANDS).allSatisfy((key, members) -> assertThat(members).containsExactly(0));
    }

    @Test
    void aSignatureHasOneKeyPerBandNamingTheBandAndItsRows() {
        long[] signature = new long[MinHash.HASHES];

        assertThat(NearDuplicates.bandKeys(signature)).hasSize(NearDuplicates.BANDS)
                .first().isEqualTo("0:[0, 0, 0, 0]");
    }

    @Test
    void onlyPairsInABucketAtTheThresholdOrAboveAreJoined() {
        List<Set<String>> features = List.of(packages(0, 9), packages(1, 10), packages(5, 14));
        int[] parent = {0, 1, 2};

        NearDuplicates.mergeSimilar(List.of(0, 1, 2), features, parent);

        assertThat(parent).containsExactly(0, 0, 2);
    }

    @Test
    void theMergedGroupTakesTheLargestMembersKeyPackagesAndFamilyAndTiesGoToTheFirstKey() {
        Group small = new Group("a", List.of("a"), 1, Set.of("x"), "alpine");
        Group large = new Group("b", List.of("b"), 5, Set.of("y"), "ubi");
        Group tie = new Group("c", List.of("c"), 5, Set.of("z"), "ubi");

        assertThat(NearDuplicates.merge(List.of(small, large, tie)))
                .isEqualTo(new Group("b", List.of("a", "b", "c"), 11, Set.of("y"), "ubi"));
    }

    @Test
    void findFollowsParentsToTheRootAndUnionJoinsUnderTheSmallerRoot() {
        int[] parent = {0, 0, 1, 3};

        assertThat(NearDuplicates.find(parent, 2)).isZero();
        NearDuplicates.union(parent, 3, 2);
        assertThat(parent[3]).isZero();
        NearDuplicates.union(parent, 3, 0);
        assertThat(parent).containsExactly(0, 0, 0, 0);
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
