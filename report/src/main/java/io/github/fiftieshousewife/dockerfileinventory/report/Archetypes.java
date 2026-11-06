package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Ranks the final groups by the locations they cover, and finds k80 and k95. */
public final class Archetypes {

    private Archetypes() {
    }

    public static Ranking rank(List<Group> groups, Estate estate) {
        List<Group> sorted = groups.stream()
                .sorted(Comparator.comparingInt(Group::locations).reversed().thenComparing(Group::key))
                .toList();
        List<Archetype> archetypes = IntStream.range(0, sorted.size())
                .mapToObj(i -> new Archetype(i + 1, sorted.get(i).key(), sorted.get(i).locations()))
                .toList();
        int total = sorted.stream().mapToInt(Group::locations).sum();
        return new Ranking(archetypes, total, covering(sorted, total, 0.80), covering(sorted, total, 0.95));
    }

    /** The fewest leading groups whose locations reach the share of the total. */
    static int covering(List<Group> sorted, int total, double share) {
        long covered = 0;
        for (int i = 0; i < sorted.size(); i++) {
            covered += sorted.get(i).locations();
            if (covered >= Math.ceil(share * total)) {
                return i + 1;
            }
        }
        return sorted.size();
    }
}
