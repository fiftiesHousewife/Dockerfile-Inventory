package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The dashboard's numbers, computed once from the same objects the tables are written from. Shares are of
 * locations (Q3), except the parse-failure rate, which is of distinct Dockerfiles as Phase 1 reports it, leaving
 * out templates saved under a Dockerfile's name. Users are
 * effective (from lineage) where a Dockerfile has a linked image, and as written otherwise (D4).
 */
public record Summary(int locations, int missing, List<PassCount> passes, int nearDuplicateGroups, int k80, int k95,
                      double parseFailureRate, double opaqueRunRate, double pinnedShare, double eolShare,
                      Map<String, Integer> managers,
                      Map<String, Integer> users, boolean effectiveUsers, double releasedShare, int templates,
                      int otherShellRuns) {

    public Summary {
        passes = List.copyOf(passes);
        managers = Collections.unmodifiableMap(new TreeMap<>(managers));
        users = Collections.unmodifiableMap(new TreeMap<>(users));
    }

    /** How the facts stage starts a template's failure message (facts' {@code FactsParser.TEMPLATE}). */
    static final String TEMPLATE = "a template, not a Dockerfile";

    static Summary of(Estate estate, List<PassCount> passes, int nearDuplicateGroups, Ranking ranking,
                      List<BaseRow> bases) {
        List<Location> all = Stream.concat(estate.dockerfiles().stream().flatMap(d -> d.locations().stream()),
                estate.failures().stream().flatMap(f -> f.locations().stream())).toList();
        int blobs = estate.dockerfiles().size() + estate.failures().size();
        int templates = (int) estate.failures().stream().filter(f -> f.message().startsWith(TEMPLATE)).count();
        int runs = estate.dockerfiles().stream().mapToInt(Dockerfile::runs).sum();
        int opaque = estate.dockerfiles().stream().mapToInt(Dockerfile::opaqueRuns).sum();
        List<BaseRow> pinnable = bases.stream().filter(b -> !b.path().equals("scratch")).toList();
        int pinned = pinnable.stream().mapToInt(BaseRow::pinnedLocations).sum();
        int pinnableLocations = pinnable.stream().mapToInt(BaseRow::locations).sum();
        int baseLocations = bases.stream().mapToInt(BaseRow::locations).sum();
        int eol = bases.stream().filter(b -> b.status().equals("eol")).mapToInt(BaseRow::locations).sum();
        Map<String, Integer> managers = new TreeMap<>();
        Map<String, Integer> users = new TreeMap<>();
        estate.dockerfiles().forEach(d -> {
            d.managers().forEach(m -> managers.merge(m, d.locations().size(), Integer::sum));
            UserKind effective = estate.effectiveUsers().get(d.blobSha());
            users.merge(effective == null ? d.userKind() : effective.name(), d.locations().size(), Integer::sum);
        });
        long released = all.stream().filter(l -> l.scope().equals("RELEASED")).count();
        return new Summary(all.size(), estate.missing().size(), passes, nearDuplicateGroups, ranking.k80(),
                ranking.k95(), share(estate.failures().size() - templates, blobs - templates), share(opaque, runs),
                share(pinned, pinnableLocations), share(eol, baseLocations), managers, users,
                !estate.effectiveUsers().isEmpty(),
                share(released, all.size()), templates,
                estate.dockerfiles().stream().mapToInt(Dockerfile::otherShellRuns).sum());
    }

    private static double share(long part, long whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }
}
