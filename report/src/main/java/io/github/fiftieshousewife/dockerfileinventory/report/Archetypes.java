package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class Archetypes {

    private Archetypes() {
    }

    static Ranking rank(List<Group> groups, Estate estate) {
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
        List<Integer> counts = sorted.stream().map(Group::locations).toList();
        return new Ranking(archetypes, total, Coverage.fewestCovering(counts, total, 80),
                Coverage.fewestCovering(counts, total, 95));
    }

    static Archetype archetype(int rank, Group group, Map<String, Dockerfile> dockerfiles,
                               Map<String, List<Location>> locations) {
        String representative = representative(group, locations);
        Dockerfile dockerfile = dockerfiles.get(representative);
        List<String> repositories = group.blobShas().stream()
                .flatMap(sha -> locations.getOrDefault(sha, List.of()).stream().map(Location::repository))
                .distinct()
                .sorted()
                .toList();
        return new Archetype(rank, group.key(), group.locations(), group.blobShas(),
                dockerfile == null ? group.key() : dockerfile.signature(), representative, repositories);
    }

    static String representative(Group group, Map<String, List<Location>> locations) {
        return group.blobShas().stream()
                .max(Comparator.comparingInt((String sha) -> locations.getOrDefault(sha, List.of()).size())
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }
}
