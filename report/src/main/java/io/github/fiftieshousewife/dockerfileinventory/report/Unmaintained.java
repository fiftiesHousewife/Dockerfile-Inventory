package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class Unmaintained {

    private Unmaintained() {
    }

    static List<String> reasons(RegistryImage image, Instant newestPush, int staleAfterMonths) {
        List<String> reasons = new ArrayList<>();
        if (image.newerBase() != null) {
            reasons.add("not rebuilt on its updated base " + image.newerBase());
        }
        if (image.lastPushed() != null && Instant.parse(image.lastPushed())
                .isBefore(newestPush.atZone(ZoneOffset.UTC).minusMonths(staleAfterMonths).toInstant())) {
            reasons.add("not published since " + image.lastPushed().substring(0, "yyyy-mm-dd".length()));
        }
        return reasons;
    }

    static List<String> archived(List<CatalogueEntry.Place> places, Set<String> archivedRepositories) {
        return !places.isEmpty() && places.stream().allMatch(p -> archivedRepositories.contains(p.repository()))
                ? List.of("every repository holding it is archived") : List.of();
    }

    static Set<String> archivedRepositories(Path discoveryDir) {
        return JsonLines.read(discoveryDir.resolve("repos.jsonl")).stream()
                .filter(row -> row.path("archived").asBoolean())
                .map(row -> row.path("repository").asString()).collect(Collectors.toSet());
    }
}
