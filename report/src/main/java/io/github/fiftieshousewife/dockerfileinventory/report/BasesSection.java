package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.h2;

import java.util.List;
import java.util.stream.Collectors;

final class BasesSection {

    static final int TOP = 20;

    private BasesSection() {
    }

    static String render(List<BaseRow> bases) {
        return String.join("\n",
                h2("Top " + TOP + " bases", "Top bases") + noEolMatch(bases),
                "<p class=\"legend\"><span class=\"key eol\"></span>end of life <span class=\"key supported\"></span>"
                        + "supported <span class=\"key unknown\"></span>not on the list "
                        + "<span class=\"key hatched\"></span>hatched: pinned by digest</p>",
                BasesChart.render(bases),
                "<details><summary>Table</summary><div class=\"scroll\">" + table(bases) + "</div></details>");
    }

    static String table(List<BaseRow> bases) {
        String rows = bases.stream().limit(TOP)
                .map(b -> "<tr><td class=\"code\">" + escape(b.path()) + "</td><td class=\"code\">"
                        + escape(b.tag() == null ? "" : b.tag()) + "</td><td class=\"num\">" + count(b.locations())
                        + "</td><td class=\"num\">" + count(b.pinnedLocations()) + "</td><td>" + escape(b.status())
                        + "</td></tr>")
                .collect(Collectors.joining());
        return "<table><tr><th>Image</th><th>Tag</th><th class=\"num\">Locations</th>"
                + "<th class=\"num\">Pinned</th><th>Status</th></tr>" + rows + "</table>";
    }

    static String noEolMatch(List<BaseRow> bases) {
        return bases.isEmpty() || bases.stream().anyMatch(b -> !b.status().equals("unknown")) ? ""
                : "<p class=\"note\">No base matched an end-of-life list, so every status is unknown: either no "
                        + "list was given or it names none of these bases. Give the internal list as a CSV with "
                        + "EOL_LIST (imagePath,tagPrefix,status,eolDate).</p>";
    }
}
