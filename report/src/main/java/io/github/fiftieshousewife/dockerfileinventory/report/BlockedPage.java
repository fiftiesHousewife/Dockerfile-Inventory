package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class BlockedPage {

    record Blocker(Hierarchies.Step step, int images, Set<String> dockerfiles) {
    }

    private BlockedPage() {
    }

    static String body(List<CatalogueEntry> entries) {
        List<Blocker> blockers = blockers(entries);
        if (blockers.isEmpty()) {
            return "<p>No image is held back by an unmaintained image in its chain.</p>";
        }
        return "<p>Each image below is unmaintained and stands in the chain of others, which cannot take a fresh base"
                + " until it is rebuilt or replaced.</p><div class=\"scroll\"><table><tr><th>Image</th><th>Why"
                + "</th><th>Images held back</th><th>Dockerfiles building them</th></tr>"
                + blockers.stream().map(BlockedPage::row).collect(Collectors.joining()) + "</table></div>";
    }

    static List<Blocker> blockers(List<CatalogueEntry> entries) {
        Map<String, Hierarchies.Step> steps = new LinkedHashMap<>();
        Map<String, Set<String>> images = new LinkedHashMap<>();
        Map<String, Set<String>> dockerfiles = new LinkedHashMap<>();
        entries.stream().filter(e -> e.hierarchy() != null).forEach(entry -> entry.hierarchy().images()
                .forEach(image -> image.blockedBy().forEach(step -> {
                    steps.putIfAbsent(step.configDigest(), step);
                    images.computeIfAbsent(step.configDigest(), key -> new TreeSet<>()).add(image.configDigest());
                    dockerfiles.computeIfAbsent(step.configDigest(), key -> new TreeSet<>()).add(entry.blobSha());
                })));
        return steps.values().stream()
                .map(step -> new Blocker(step, images.get(step.configDigest()).size(),
                        dockerfiles.get(step.configDigest())))
                .sorted(Comparator.comparingInt(Blocker::images).reversed()
                        .thenComparing(blocker -> blocker.step().configDigest()))
                .toList();
    }

    private static String row(Blocker blocker) {
        return "<tr><td>" + escape(String.join(", ", blocker.step().names())) + "</td><td>"
                + escape(String.join("; ", blocker.step().unmaintained())) + "</td><td>" + blocker.images()
                + "</td><td>" + blocker.dockerfiles().stream().map(sha -> Html.shaLink("dockerfiles/", sha))
                .collect(Collectors.joining(" ")) + "</td></tr>";
    }
}
