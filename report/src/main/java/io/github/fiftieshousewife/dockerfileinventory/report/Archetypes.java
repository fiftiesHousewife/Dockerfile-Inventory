package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Ranks the final groups by the locations they cover, and finds k80 and k95. */
public final class Archetypes {

    private Archetypes() {
    }

    public static Ranking rank(List<Group> groups, Estate estate) {
        Map<String, Dockerfile> dockerfiles = new HashMap<>();
        estate.dockerfiles().forEach(d -> dockerfiles.put(d.blobSha(), d));
        Map<String, List<Location>> locations = new HashMap<>();
        estate.dockerfiles().forEach(d -> locations.put(d.blobSha(), d.locations()));
        estate.failures().forEach(f -> locations.put(f.blobSha(), f.locations()));
        List<Group> sorted = groups.stream()
                .sorted(Comparator.comparingInt(Group::locations).reversed().thenComparing(Group::key))
                .toList();
        List<Archetype> archetypes = IntStream.range(0, sorted.size())
                .mapToObj(i -> archetype(i + 1, sorted.get(i), dockerfiles, locations))
                .toList();
        int total = sorted.stream().mapToInt(Group::locations).sum();
        return new Ranking(archetypes, total, covering(sorted, total, 80), covering(sorted, total, 95));
    }

    /** The representative is the member at the most locations, the Dockerfile to read to understand the group. */
    private static Archetype archetype(int rank, Group group, Map<String, Dockerfile> dockerfiles,
                                       Map<String, List<Location>> locations) {
        String representative = group.blobShas().stream()
                .max(Comparator.comparingInt((String sha) -> locations.getOrDefault(sha, List.of()).size())
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
        Dockerfile dockerfile = dockerfiles.get(representative);
        List<String> repositories = group.blobShas().stream()
                .flatMap(sha -> locations.getOrDefault(sha, List.of()).stream().map(Location::repository))
                .distinct()
                .sorted()
                .toList();
        return new Archetype(rank, group.key(), group.locations(), group.blobShas(),
                dockerfile == null ? group.key() : dockerfile.signature(), representative, repositories);
    }

    /** The fewest leading groups whose locations reach {@code percent} of the total, in whole numbers. */
    static int covering(List<Group> sorted, int total, int percent) {
        long covered = 0;
        for (int i = 0; i < sorted.size(); i++) {
            covered += sorted.get(i).locations();
            if (covered * 100 >= (long) percent * total) {
                return i + 1;
            }
        }
        return sorted.size();
    }
}
