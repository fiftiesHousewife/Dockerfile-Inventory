package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.meter;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.plural;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Counts as plain bars: one row per name, largest first, the number beside its bar.
final class CountTable {

    private CountTable() {
    }

    static String render(String heading, Map<String, Integer> counts, Map<String, Integer> organisations, int limit) {
        int largest = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        return "<div class=\"scroll\"><table><tr><th>" + escape(heading) + "</th><th class=\"num\">Locations</th>"
                + "<th></th>" + (organisations.isEmpty() ? "" : "<th class=\"num\">Organisations</th>") + "</tr>"
                + counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(e -> "<tr><td>" + escape(e.getKey()) + "</td><td class=\"num\">" + count(e.getValue())
                        + "</td><td>" + meter((double) e.getValue() / largest) + "</td>" + (organisations.isEmpty()
                        ? "" : "<td class=\"num\">" + count(organisations.getOrDefault(e.getKey(), 0)) + "</td>")
                        + "</tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }

    static Map<String, Integer> bases(Map<String, Integer> families) {
        return families.entrySet().stream().filter(e -> !AppliesTo.NOT_BASES.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum, TreeMap::new));
    }

    static String leftOut(Map<String, Integer> families) {
        List<String> parts = new ArrayList<>();
        if (families.containsKey("scratch")) {
            parts.add("scratch, the empty base, at " + plural(families.get("scratch"), "location", "locations"));
        }
        if (families.containsKey(BaseCatalogue.UNRESOLVED)) {
            parts.add("a base set by a variable that could not be resolved at "
                    + plural(families.get(BaseCatalogue.UNRESOLVED), "location", "locations"));
        }
        return parts.isEmpty() ? "" : "<p class=\"note\">Left out: " + String.join("; ", parts) + ".</p>";
    }

    static Map<String, Integer> organisationsUsing(List<Figures> organisations) {
        return organisations.stream().flatMap(f -> f.families().keySet().stream())
                .collect(Collectors.toMap(family -> family, family -> 1, Integer::sum, TreeMap::new));
    }
}
