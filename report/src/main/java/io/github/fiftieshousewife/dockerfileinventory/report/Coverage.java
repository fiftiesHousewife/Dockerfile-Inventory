package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

final class Coverage {

    private Coverage() {
    }

    static int fewestCovering(List<Integer> locations, int total, int percent) {
        long[] covered = locations.stream().mapToLong(Integer::longValue).toArray();
        Arrays.parallelPrefix(covered, Long::sum);
        return IntStream.range(0, covered.length).filter(i -> covered[i] * 100 >= (long) percent * total)
                .map(i -> i + 1).findFirst().orElse(locations.size());
    }
}
