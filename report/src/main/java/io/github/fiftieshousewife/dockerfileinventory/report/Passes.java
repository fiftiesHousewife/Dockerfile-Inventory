package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The exact collapse passes, cheapest first: blob SHA, text-normal hash, signature hash. Parse failures have no
 * hashes: they stay one group each until the signature pass, which puts them all in one group (D5).
 */
public final class Passes {

    static final String PARSE_FAILURES = "parse-failures";

    private Passes() {
    }

    public static List<PassCount> exact(Estate estate) {
        return List.of(
                count("blob", groups(estate, Dockerfile::blobSha, false)),
                count("text-normal", groups(estate, Dockerfile::textHash, false)),
                count("signature", signatureGroups(estate)));
    }

    /** The groups the near-duplicate pass starts from. */
    public static List<Group> signatureGroups(Estate estate) {
        return groups(estate, Dockerfile::signatureHash, true);
    }

    private static List<Group> groups(Estate estate, Function<Dockerfile, String> key, boolean failuresTogether) {
        Map<String, List<Dockerfile>> byKey = new LinkedHashMap<>();
        estate.dockerfiles().forEach(d -> byKey.computeIfAbsent(key.apply(d), k -> new ArrayList<>()).add(d));
        List<Group> groups = new ArrayList<>();
        byKey.forEach((k, members) -> groups.add(new Group(k, members.stream().map(Dockerfile::blobSha).toList(),
                members.stream().mapToInt(d -> d.locations().size()).sum(),
                members.getFirst().finalPackages(), family(members.getFirst().base()))));
        if (failuresTogether && !estate.failures().isEmpty()) {
            groups.add(new Group(PARSE_FAILURES, estate.failures().stream().map(Failure::blobSha).toList(),
                    estate.failures().stream().mapToInt(f -> f.locations().size()).sum(), java.util.Set.of(),
                    PARSE_FAILURES));
        } else {
            estate.failures().forEach(f -> groups.add(new Group(f.blobSha(), List.of(f.blobSha()),
                    f.locations().size(), java.util.Set.of(), PARSE_FAILURES)));
        }
        return groups;
    }

    /** The facts stage's D4: the last path segment of the base's name. */
    static String family(Base base) {
        String name = base.name() == null ? "" : base.name();
        return name.contains("$") ? "unresolved" : name.substring(name.lastIndexOf('/') + 1);
    }

    private static PassCount count(String pass, List<Group> groups) {
        return new PassCount(pass, groups.size(), groups.stream().mapToInt(Group::locations).sum());
    }
}
