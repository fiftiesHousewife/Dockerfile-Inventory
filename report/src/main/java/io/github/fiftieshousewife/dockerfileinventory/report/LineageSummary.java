package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.plural;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.Counts;
import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.OutdatedRow;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class LineageSummary {

    // Strongest first, each with what it means for the image.
    static final Map<String, String> TIERS = Map.of(
            "STRONG", "its build steps match a Dockerfile exactly, read at a released commit",
            "PROBABLE", "its build steps match a Dockerfile exactly, read on the default branch",
            "WEAK", "the closest Dockerfile on the same base: similar steps, not an exact match",
            "NONE", "no Dockerfile matches");
    static final List<String> ORDER = List.of("STRONG", "PROBABLE", "WEAK", "NONE");

    private LineageSummary() {
    }

    static String render(Optional<LineageView> lineage, String up) {
        return Html.h2("Lineage", "Lineage") + lineage.map(view -> "<p>" + plural(view.counts().images(),
                        "image", "images") + " in Artifactory; parent identified for "
                        + percent(view.counts().coverage()) + " (external roots included); "
                        + plural(view.counts().externalBases(), "base", "bases") + " built outside. "
                        + "<a href=\"" + up + "lineage.html\">The family tree</a></p>" + confidence(view.counts())
                        + outdated(view.outdated()) + gaps(view.gaps()))
                .orElse("<p class=\"note\">Lineage has not run: no images from Artifactory are linked to "
                        + "Dockerfiles. Give LINEAGE_DIR to add it.</p>");
    }

    static String confidence(Counts counts) {
        int total = counts.linksByTier().values().stream().mapToInt(Integer::intValue).sum();
        List<Integer> shown = IntStream.range(0, ORDER.size())
                .filter(i -> counts.linksByTier().getOrDefault(ORDER.get(i), 0) > 0).boxed().toList();
        return "<h3>How sure each link to a Dockerfile is</h3><span class=\"mix wide\" role=\"img\" aria-label=\""
                + "link confidence\">" + shown.stream().map(i -> "<span class=\"s" + (i + 1) + "\" style=\"width:"
                        + percent(share(counts, ORDER.get(i), total)) + "\"></span>").collect(Collectors.joining())
                + "</span><ul class=\"legend-list\">" + shown.stream().map(i -> "<li><span class=\"key s" + (i + 1)
                        + "\"></span>" + label(ORDER.get(i)) + " " + count(counts.linksByTier().get(ORDER.get(i)))
                        + " (" + percent(share(counts, ORDER.get(i), total)) + "): " + TIERS.get(ORDER.get(i))
                        + "</li>").collect(Collectors.joining()) + "</ul>";
    }

    private static double share(Counts counts, String tier, int total) {
        return total == 0 ? 0 : (double) counts.linksByTier().getOrDefault(tier, 0) / total;
    }

    static String label(String tier) {
        return tier.toLowerCase(Locale.ROOT);
    }

    static String outdated(List<OutdatedRow> outdated) {
        return "<h3>On an outdated base (" + count(outdated.size()) + ")</h3>" + (outdated.isEmpty()
                ? "<p class=\"note\">No image is built on a base that has been pushed again since.</p>"
                : "<div class=\"scroll\"><table><tr><th>Image</th><th>Built on</th><th>Newer base</th></tr>"
                        + outdated.stream().map(o -> "<tr><td class=\"code\">" + escape(o.image())
                                + "</td><td class=\"code\">" + escape(o.base()) + "</td><td class=\"code\">"
                                + escape(o.newer()) + "</td></tr>").collect(Collectors.joining())
                        + "</table></div>");
    }

    static String gaps(List<String> gaps) {
        return "<h3>No Dockerfile found (" + count(gaps.size()) + ")</h3>" + (gaps.isEmpty()
                ? "<p class=\"note\">Every image with a parent in Artifactory has a Dockerfile.</p>"
                : "<ul>" + gaps.stream().map(g -> "<li><code>" + escape(g) + "</code></li>")
                        .collect(Collectors.joining()) + "</ul>");
    }
}
