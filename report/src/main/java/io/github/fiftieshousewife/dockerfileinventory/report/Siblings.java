package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Siblings {

    record Found(List<String> blobShas, int count) {

        Found {
            blobShas = List.copyOf(blobShas);
        }
    }

    private static final int LISTED = 50;
    private static final Found NONE = new Found(List.of(), 0);

    private Siblings() {
    }

    static Map<String, Found> of(List<Group> groups) {
        Map<String, Found> siblings = new HashMap<>();
        groups.forEach(group -> {
            List<String> sorted = group.blobShas().stream().sorted().toList();
            List<String> first = sorted.stream().limit(LISTED + 1).toList();
            sorted.forEach(sha -> siblings.put(sha, new Found(first.stream().filter(other -> !other.equals(sha))
                    .limit(LISTED).toList(), sorted.size() - 1)));
        });
        return siblings;
    }

    static Found of(Map<String, Found> siblings, String blobSha) {
        return siblings.getOrDefault(blobSha, NONE);
    }
}
