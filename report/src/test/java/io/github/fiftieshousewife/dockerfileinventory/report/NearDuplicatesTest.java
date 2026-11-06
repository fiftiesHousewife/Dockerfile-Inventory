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

    static Group group(String key, int locations, Set<String> packages) {
        return new Group(key, List.of(key), locations, packages, "ubi-minimal");
    }

    static Set<String> packages(int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> "pkg-" + i).collect(Collectors.toCollection(TreeSet::new));
    }
}
