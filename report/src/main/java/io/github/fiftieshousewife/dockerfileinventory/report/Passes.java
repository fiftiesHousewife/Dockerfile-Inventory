package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

final class Passes {

    static final String PARSE_FAILURES = "parse-failures";
    static final String TEMPLATES = "templates";

    private Passes() {
    }

    static List<PassCount> exact(Estate estate) {
        return List.of(
                count("blob", groups(estate, Dockerfile::blobSha, false)),
                count("text-normal", groups(estate, Dockerfile::textHash, false)),
                count("signature", signatureGroups(estate)));
    }

    static List<Group> signatureGroups(Estate estate) {
        return groups(estate, Dockerfile::signatureHash, true);
    }

    static List<Group> groups(Estate estate, Function<Dockerfile, String> key, boolean failuresTogether) {
        return Stream.concat(dockerfileGroups(estate, key).stream(),
                failureGroups(estate, failuresTogether).stream()).toList();
    }

    static List<Group> dockerfileGroups(Estate estate, Function<Dockerfile, String> key) {
        Map<String, List<Dockerfile>> byKey = new LinkedHashMap<>();
        estate.dockerfiles().forEach(d -> byKey.computeIfAbsent(key.apply(d), k -> new ArrayList<>()).add(d));
        return byKey.entrySet().stream().map(e -> new Group(e.getKey(),
                e.getValue().stream().map(Dockerfile::blobSha).toList(),
                e.getValue().stream().mapToInt(d -> d.locations().size()).sum(),
                e.getValue().getFirst().finalPackages(), family(e.getValue().getFirst().base()))).toList();
    }

    static List<Group> failureGroups(Estate estate, boolean together) {
        Map<String, List<Failure>> byKind = new LinkedHashMap<>();
        estate.failures().forEach(f -> byKind.computeIfAbsent(kind(f), k -> new ArrayList<>()).add(f));
        return byKind.entrySet().stream().flatMap(e -> together
                ? Stream.of(new Group(e.getKey(), e.getValue().stream().map(Failure::blobSha).toList(),
                        e.getValue().stream().mapToInt(f -> f.locations().size()).sum(), Set.of(), e.getKey()))
                : e.getValue().stream().map(f -> new Group(f.blobSha(), List.of(f.blobSha()), f.locations().size(),
                        Set.of(), e.getKey()))).toList();
    }

    static String kind(Failure failure) {
        return failure.message().startsWith(Summary.TEMPLATE) ? TEMPLATES : PARSE_FAILURES;
    }

    // facts' D4: the last path segment of the base's name.
    static String family(Base base) {
        String name = base.name() == null ? "" : base.name();
        return name.contains("$") ? "unresolved" : name.substring(name.lastIndexOf('/') + 1);
    }

    static PassCount count(String pass, List<Group> groups) {
        return new PassCount(pass, groups.size(), groups.stream().mapToInt(Group::locations).sum());
    }
}
