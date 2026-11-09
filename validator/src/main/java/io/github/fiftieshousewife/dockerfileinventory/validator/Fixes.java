package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Actions grouped into fixes: the same check on an archetype's copies is one change to its template.
final class Fixes {

    private static final Comparator<Fix> URGENCY = Comparator.comparing(Fix::severity)
            .thenComparing(Comparator.comparingInt(Fix::locations).reversed())
            .thenComparing(Fix::certainty)
            .thenComparing(Fix::check)
            .thenComparing(Fix::representative);

    private Fixes() {
    }

    static List<Fix> of(List<Action> actions, Catalogue catalogue, Map<String, Subject> subjects,
                        Map<String, List<Location>> locations) {
        Map<String, List<Action>> groups = actions.stream().collect(Collectors.groupingBy(
                action -> catalogue.archetype(action.blobSha()) + " " + action.check(), LinkedHashMap::new,
                Collectors.toList()));
        return groups.values().stream().map(group -> fix(group, catalogue, subjects, locations))
                .sorted(URGENCY).toList();
    }

    static Fix fix(List<Action> group, Catalogue catalogue, Map<String, Subject> subjects,
                   Map<String, List<Location>> locations) {
        Action first = group.getFirst();
        List<String> copies = group.stream().map(Action::blobSha).sorted().toList();
        List<String> inheriting = inheriting(first.check(), copies, catalogue, subjects);
        int reached = Stream.concat(copies.stream(), inheriting.stream())
                .mapToInt(sha -> locations.getOrDefault(sha, List.of()).size()).sum();
        Certainty surest = group.stream().map(Action::certainty).min(Comparator.naturalOrder()).orElseThrow();
        String representative = representative(group, catalogue.representative(catalogue.archetype(first.blobSha())));
        List<String> because = group.stream().filter(action -> action.blobSha().equals(representative)).findFirst()
                .orElseThrow().because();
        return new Fix(first.check(), first.severity(), surest, first.what(), representative, because, copies,
                inheriting, reached);
    }

    // Down the Dockerfiles built on the copies, as far as each keeps what the fix changes.
    static List<String> inheriting(Check check, List<String> copies, Catalogue catalogue,
                                   Map<String, Subject> subjects) {
        Set<String> seen = new HashSet<>(copies);
        Deque<String> next = new ArrayDeque<>(copies);
        Set<String> reached = new TreeSet<>();
        while (!next.isEmpty()) {
            catalogue.children(next.pop()).stream()
                    .filter(child -> subjects.containsKey(child) && Inheritance.inherits(check, subjects.get(child)))
                    .filter(seen::add)
                    .forEach(child -> {
                        reached.add(child);
                        next.add(child);
                    });
        }
        return List.copyOf(reached);
    }

    static String representative(List<Action> group, String archetypes) {
        return group.stream().map(Action::blobSha).anyMatch(sha -> sha.equals(archetypes)) ? archetypes
                : group.stream().min(Comparator.comparingInt(Action::reach).reversed()
                        .thenComparing(Action::blobSha)).orElseThrow().blobSha();
    }
}
