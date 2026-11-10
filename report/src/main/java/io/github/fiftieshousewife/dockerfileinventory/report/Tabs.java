package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.List;
import java.util.stream.Collectors;

// The summary's tabs: All, the combined estate, then each organisation's summary.
final class Tabs {

    private Tabs() {
    }

    static String render(List<String> organisations, String current, String up) {
        if (organisations.size() < 2) {
            return "";
        }
        return "<nav class=\"tabs\" aria-label=\"Organisations\">" + tab(up + "index.html", "All", current == null)
                + organisations.stream().sorted().map(org -> tab(up + "orgs/" + escape(org) + ".html", org,
                        org.equals(current))).collect(Collectors.joining()) + "</nav>";
    }

    private static String tab(String href, String label, boolean current) {
        return "<a href=\"" + href + "\"" + (current ? " class=\"current\" aria-current=\"page\"" : "") + ">"
                + escape(label) + "</a>";
    }
}
