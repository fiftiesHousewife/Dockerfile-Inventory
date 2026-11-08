package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;

final class GraphQlHistory {

    private static final String REPOSITORY = "  %s: repository(owner: %s, name: %s)"
            + " { object(oid: %s) { ... on Commit {%n%s%n  } } }";
    private static final String PATH = "    %s: history(first: 1, path: %s) { nodes { oid committedDate } }";

    record Alias(String repository, String path) {
    }

    private GraphQlHistory() {
    }

    static String query(List<ChangeRequest> requests) {
        Map<String, List<ChangeRequest>> byCommit = byRepositoryAndCommit(requests);
        Map<ChangeRequest, Alias> aliases = aliases(byCommit);
        return byCommit.values().stream().map(group -> {
            ChangeRequest first = group.getFirst();
            String paths = group.stream().map(r -> PATH.formatted(aliases.get(r).path(),
                    GraphQlBlobs.quoted(r.path()))).collect(Collectors.joining("\n"));
            return REPOSITORY.formatted(aliases.get(first).repository(),
                    GraphQlBlobs.quoted(GraphQlBlobs.owner(first.repository())),
                    GraphQlBlobs.quoted(GraphQlBlobs.name(first.repository())),
                    GraphQlBlobs.quoted(first.commitSha()), paths);
        }).collect(Collectors.joining("\n", "query {\n", "\n}"));
    }

    static List<LastChange> read(JsonNode data, List<ChangeRequest> requests) {
        Map<ChangeRequest, Alias> aliases = aliases(byRepositoryAndCommit(requests));
        return aliases.entrySet().stream().map(e -> lastChange(e.getKey(), data.path(e.getValue().repository())
                .path("object").path(e.getValue().path()).path("nodes").path(0)))
                .filter(change -> change.changedIn() != null).toList();
    }

    static Map<String, List<ChangeRequest>> byRepositoryAndCommit(List<ChangeRequest> requests) {
        Map<String, List<ChangeRequest>> groups = new LinkedHashMap<>();
        requests.stream().distinct().forEach(r -> groups.computeIfAbsent(r.repository() + "@" + r.commitSha(),
                key -> new ArrayList<>()).add(r));
        return groups;
    }

    static Map<ChangeRequest, Alias> aliases(Map<String, List<ChangeRequest>> byCommit) {
        Map<ChangeRequest, Alias> aliases = new LinkedHashMap<>();
        List<List<ChangeRequest>> groups = List.copyOf(byCommit.values());
        IntStream.range(0, groups.size()).forEach(g -> IntStream.range(0, groups.get(g).size())
                .forEach(p -> aliases.put(groups.get(g).get(p), new Alias("r" + g, "p" + p))));
        return aliases;
    }

    private static LastChange lastChange(ChangeRequest request, JsonNode node) {
        return new LastChange(request.repository(), request.commitSha(), request.path(),
                node.path("oid").stringValue(null), node.path("committedDate").stringValue(null));
    }
}
