package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.plural;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class BasesPage {

    private BasesPage() {
    }

    static String body(List<BaseRow> bases) {
        boolean statuses = bases.stream().anyMatch(b -> !b.status().equals("unknown"));
        Map<String, List<BaseRow>> byFamily = bases.stream().filter(b -> !AppliesTo.NOT_BASES.contains(b.family()))
                .collect(Collectors.groupingBy(BaseRow::family, TreeMap::new, Collectors.toList()));
        return "<p class=\"note\">Every base the final stage of a Dockerfile builds FROM, grouped by family (the last "
                + "part of its name), most locations first. Metadata-only bundles are left out.</p>"
                + (statuses ? "" : noEolMatch())
                + CountTable.leftOut(bases.stream().collect(Collectors.toMap(BaseRow::family, BaseRow::locations,
                        Integer::sum)))
                + byFamily.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<BaseRow>> e) -> -locations(e.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .map(e -> family(e.getKey(), e.getValue(), statuses)).collect(Collectors.joining());
    }

    private static int locations(List<BaseRow> rows) {
        return rows.stream().mapToInt(BaseRow::locations).sum();
    }

    static String family(String family, List<BaseRow> rows, boolean statuses) {
        int pinned = rows.stream().mapToInt(BaseRow::pinnedLocations).sum();
        return "<details><summary><strong>" + escape(family) + "</strong>: "
                + plural(locations(rows), "location", "locations") + " on " + plural(rows.size(), "base", "bases")
                + ", " + percent((double) pinned / Math.max(1, locations(rows))) + " pinned by digest</summary>"
                + "<div class=\"scroll\"><table><tr><th>Image</th><th>Tag</th><th class=\"num\">Locations</th>"
                + "<th class=\"num\">Pinned</th>" + (statuses ? "<th>Status</th>" : "") + "</tr>"
                + rows.stream().map(b -> "<tr><td class=\"code\">" + escape(b.path()) + "</td><td class=\"code\">"
                        + escape(b.tag() == null ? "" : b.tag()) + "</td><td class=\"num\">" + count(b.locations())
                        + "</td><td class=\"num\">" + count(b.pinnedLocations()) + "</td>"
                        + (statuses ? "<td>" + status(b) + "</td>" : "") + "</tr>").collect(Collectors.joining())
                + "</table></div></details>";
    }

    static String status(BaseRow base) {
        return switch (base.status()) {
            case "eol" -> "end of life" + (base.eolDate() == null ? "" : " (" + base.eolDate() + ")");
            case "unknown" -> "not on the list";
            default -> escape(base.status());
        };
    }

    static String noEolMatch() {
        return "<p class=\"note\">No base matched an end-of-life list, so no status is shown: either no list was "
                + "given or it names none of these bases. Give the internal list as a CSV with EOL_LIST "
                + "(imagePath,tagPrefix,status,eolDate).</p>";
    }
}
