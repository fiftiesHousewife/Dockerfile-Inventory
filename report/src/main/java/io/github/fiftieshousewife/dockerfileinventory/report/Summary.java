package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

record Summary(int locations, int missing, List<PassCount> passes, int nearDuplicateGroups, int k80, int k95,
               double parseFailureRate, double opaqueRunRate, double pinnedShare, double eolShare,
               Map<String, Integer> managers, Map<String, Integer> users, boolean effectiveUsers,
               double releasedShare, int templates, int otherShellRuns) {

    // How facts' FactsParser.TEMPLATE starts a template's failure message.
    static final String TEMPLATE = "a template, not a Dockerfile";

    Summary {
        passes = List.copyOf(passes);
        managers = Collections.unmodifiableMap(new TreeMap<>(managers));
        users = Collections.unmodifiableMap(new TreeMap<>(users));
    }

    static Summary of(Estate estate, List<PassCount> passes, int nearDuplicateGroups, Ranking ranking,
                      List<BaseRow> bases) {
        List<Location> all = Stream.concat(estate.dockerfiles().stream().flatMap(d -> d.locations().stream()),
                estate.failures().stream().flatMap(f -> f.locations().stream())).toList();
        int templates = templates(estate);
        int blobs = estate.dockerfiles().size() + estate.failures().size();
        int runs = estate.dockerfiles().stream().mapToInt(Dockerfile::runs).sum();
        int opaque = estate.dockerfiles().stream().mapToInt(Dockerfile::opaqueRuns).sum();
        long released = all.stream().filter(l -> l.scope().equals("RELEASED")).count();
        return new Summary(all.size(), estate.missing().size(), passes, nearDuplicateGroups, ranking.k80(),
                ranking.k95(), share(estate.failures().size() - templates, blobs - templates), share(opaque, runs),
                pinnedShare(bases), eolShare(bases), managers(estate), users(estate),
                !estate.effectiveUsers().isEmpty(), share(released, all.size()), templates,
                estate.dockerfiles().stream().mapToInt(Dockerfile::otherShellRuns).sum());
    }

    static int templates(Estate estate) {
        return (int) estate.failures().stream().filter(f -> f.message().startsWith(TEMPLATE)).count();
    }

    static double pinnedShare(List<BaseRow> bases) {
        List<BaseRow> pinnable = bases.stream().filter(b -> !b.path().equals("scratch")).toList();
        return share(pinnable.stream().mapToInt(BaseRow::pinnedLocations).sum(),
                pinnable.stream().mapToInt(BaseRow::locations).sum());
    }

    static double eolShare(List<BaseRow> bases) {
        return share(bases.stream().filter(b -> b.status().equals("eol")).mapToInt(BaseRow::locations).sum(),
                bases.stream().mapToInt(BaseRow::locations).sum());
    }

    static Map<String, Integer> managers(Estate estate) {
        Map<String, Integer> managers = new TreeMap<>();
        estate.dockerfiles().forEach(d ->
                d.managers().forEach(m -> managers.merge(m, d.locations().size(), Integer::sum)));
        return managers;
    }

    static Map<String, Integer> users(Estate estate) {
        Map<String, Integer> users = new TreeMap<>();
        estate.dockerfiles().forEach(d -> {
            UserKind effective = estate.effectiveUsers().get(d.blobSha());
            users.merge(effective == null ? d.userKind() : effective.name(), d.locations().size(), Integer::sum);
        });
        return users;
    }

    static double share(long part, long whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }
}
