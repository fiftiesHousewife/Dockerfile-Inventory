package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.meter;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.tile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class OverviewPage {

    static final List<String> DATA = List.of("catalogue/dockerfiles.jsonl", "archetypes.jsonl", "passes.json");

    private OverviewPage() {
    }

    static String body(EstateFigures f, List<String> organisations, String up) {
        return body(f, organisations, up, null);
    }

    static String body(EstateFigures f, List<String> organisations, String up, String lists) {
        return "<div class=\"tiles\">" + tile("Dockerfiles", count(f.dockerfiles()), up + "archetypes.html")
                + tile("Images", count(f.images()), up + "hierarchy.html")
                + tile("Locations", count(f.locations()), up + "archetypes.html")
                + tile("Organisations", count(f.organisations()), organisations.isEmpty() ? up + "index.html"
                        : "#organisations")
                + tile("Did not parse", count(f.unparsed()), up + "unparsed.html") + "</div>"
                + "<h2>Chains and layers</h2><p>How many of the estate's own images each Dockerfile is built on top"
                + " of: 0 is built straight on an outside base (ubi9, alpine), 1 on an image another estate Dockerfile"
                + " makes, 2 on one built on another, and so on; a fix to a base is rebuilt down every level beneath"
                + " it. Then how many layers each carries; " + count(f.overLimit())
                + " over the layer limit. <a href=\"" + up + "hierarchy.html\">The hierarchy</a></p>"
                + table("Estate images beneath it", f.depths(), in(lists, "depth"))
                + table("Layers", f.layerBands(), in(lists, "layers"))
                + "<h2>Unmaintained</h2><p>" + count(f.blockedImages())
                + " images stand on an unmaintained one. <a href=\"" + up + "blocked.html\">What blocks them</a></p>"
                + table("Why", f.unmaintained(), null)
                + "<h2>The final image</h2>" + shares(f, in(lists, "final-image"))
                + "<h2>Footprints</h2>" + table("Footprint", f.footprints(), in(lists, "footprint"))
                + "<h2>Language runtimes</h2>" + table("Runtime", f.runtimes(), in(lists, "runtime"))
                + "<h2>Unusual steps</h2>" + table("Step", f.unusual(), in(lists, "unusual"))
                + "<h2>More</h2><ul id=\"organisations\"><li><a href=\"" + up + "archetypes.html\">Archetypes</a></li>"
                + organisations.stream().map(o -> "<li><a href=\"orgs/" + escape(o) + ".html\">" + escape(o)
                        + "</a></li>").collect(Collectors.joining())
                + DATA.stream().map(file -> "<li><a href=\"" + up + file + "\">" + file + "</a></li>")
                        .collect(Collectors.joining()) + "</ul>";
    }

    static String shares(EstateFigures f) {
        return shares(f, null);
    }

    static String shares(EstateFigures f, String list) {
        double all = Math.max(1, f.dockerfiles());
        return "<table class=\"tally\"><tr><th>Final image</th><th class=\"num\">Dockerfiles</th>"
                + "<th class=\"num\">Share</th></tr>"
                + EstateFigures.largestFirst(Map.of("Runs as root", f.root(), "Exposes a port below 1024",
                        f.lowPort(), "Ships a toolchain", f.toolchain())).entrySet().stream()
                .map(e -> share(labelled(e.getKey(), e.getValue() == 0 ? null : list), e.getValue(), all))
                .collect(Collectors.joining())
                + "</table>";
    }

    private static String share(String label, int n, double all) {
        return "<tr><td>" + label + "</td><td class=\"num\">" + count(n) + "</td><td class=\"num\">"
                + percent(n / all) + "</td></tr>";
    }

    private static String in(String lists, String section) {
        return lists == null ? null : lists + section + "/";
    }

    static String labelled(String label, String list) {
        return list == null ? escape(label) : "<a href=\"" + list + Drilldowns.slug(label) + ".html\">"
                + escape(label) + "</a>";
    }

    static <K> String table(String heading, Map<K, Integer> counts) {
        return table(heading, counts, null);
    }

    static <K> String table(String heading, Map<K, Integer> counts, String list) {
        int largest = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        return "<table class=\"tally\"><tr><th>" + escape(heading) + "</th><th class=\"num\">Count</th><th></th></tr>"
                + counts.entrySet().stream().map(e -> "<tr><td>" + labelled(String.valueOf(e.getKey()), list)
                        + "</td><td class=\"num\">" + count(e.getValue()) + "</td><td>"
                        + meter((double) e.getValue() / largest) + "</td></tr>").collect(Collectors.joining())
                + "</table>";
    }
}
