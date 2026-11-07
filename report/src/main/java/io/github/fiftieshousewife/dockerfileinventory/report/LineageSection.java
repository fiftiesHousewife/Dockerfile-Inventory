package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.h2;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.tile;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class LineageSection {

    private static final int TOP = 20;
    private static final int TREE_LIMIT = 50;

    private LineageSection() {
    }

    static String render(Optional<LineageView> lineage) {
        return String.join("\n", h2("Lineage", "Lineage"), lineage.map(LineageSection::view).orElse(
                "<p class=\"note\">Lineage has not run: no images from Artifactory are linked to Dockerfiles.</p>"));
    }

    static String view(LineageView view) {
        LineageView.Counts c = view.counts();
        return "<div class=\"tiles\">" + tile("Images in Artifactory", count(c.images()))
                + tile("With a known parent", percent(c.coverage()))
                + tile("Linked to a Dockerfile", count(linked(c)))
                + tile("No Dockerfile found (gaps)", count(c.gaps()))
                + tile("Built outside (external bases)", count(c.externalBases()))
                + tile("On an outdated base", count(c.outdatedBases())) + "</div>"
                + (view.images().size() < TREE_LIMIT ? LineageTree.render(view.images()) : lists(view));
    }

    static int linked(LineageView.Counts counts) {
        return counts.linksByTier().entrySet().stream()
                .filter(e -> !e.getKey().equals("NONE")).mapToInt(Map.Entry::getValue).sum();
    }

    static String lists(LineageView view) {
        return "<h3>" + heading("No Dockerfile found", view.gaps().size(), ", newest first") + "</h3><ul>"
                + gaps(view) + "</ul><h3>" + heading("On an outdated base", view.outdated().size(), "") + "</h3>"
                + "<div class=\"scroll\"><table><tr><th>Image</th><th>Built on</th><th>Newer base</th></tr>"
                + outdated(view) + "</table></div>"
                + "<h3>" + heading("Most-used bases", view.bases().size(), "") + "</h3>"
                + "<div class=\"scroll\"><table><tr><th>Base</th><th class=\"num\">Images built on it</th></tr>"
                + bases(view) + "</table></div>";
    }

    static String gaps(LineageView view) {
        return view.gaps().stream().limit(TOP).map(g -> "<li><code>" + escape(g) + "</code></li>")
                .collect(Collectors.joining());
    }

    static String outdated(LineageView view) {
        return view.outdated().stream().limit(TOP)
                .map(o -> "<tr><td class=\"code\">" + escape(o.image()) + "</td><td class=\"code\">"
                        + escape(o.base()) + "</td><td class=\"code\">" + escape(o.newer()) + "</td></tr>")
                .collect(Collectors.joining());
    }

    static String bases(LineageView view) {
        return view.bases().stream().limit(TOP)
                .map(b -> "<tr><td class=\"code\">" + escape(b.name()) + "</td><td class=\"num\">"
                        + count(b.children()) + "</td></tr>")
                .collect(Collectors.joining());
    }

    static String heading(String title, int total, String order) {
        return title + (total <= TOP ? " (" + count(total) + order + ")"
                : " (top " + TOP + " of " + count(total) + order + ")");
    }
}
