package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.meter;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.plural;

import io.github.fiftieshousewife.dockerfileinventory.report.ArchetypeRows.Row;
import io.github.fiftieshousewife.dockerfileinventory.report.ArchetypeRows.Apart;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ArchetypeTable {

    private static final int CHIPS = 3;

    private ArchetypeTable() {
    }

    static String render(List<Row> rows, int from, int to, Set<String> pages, String up) {
        int largest = rows.stream().mapToInt(Row::locations).max().orElse(1);
        return "<div class=\"scroll\"><table><tr><th class=\"num\">Rank</th><th>Archetype</th>"
                + "<th class=\"num\">Locations</th><th></th><th class=\"num\">Files</th><th>Organisations</th>"
                + "<th>Largest repository</th></tr>"
                + rows.subList(Math.min(from, rows.size()), Math.min(to, rows.size())).stream()
                .map(r -> row(r, largest, pages, up)).collect(Collectors.joining())
                + "</table></div>";
    }

    static String row(Row row, int largest, Set<String> pages, String up) {
        Map.Entry<String, Integer> repository = row.repositories().entrySet().iterator().next();
        return "<tr><td class=\"num\">" + row.rank() + "</td><td>" + name(row, pages, up) + "</td><td class=\"num\">"
                + count(row.locations()) + "</td><td>" + meter((double) row.locations() / largest) + "</td>"
                + "<td class=\"num\">" + count(row.files()) + "</td><td>" + chips(row.organisations()) + "</td><td>"
                + escape(repository.getKey()) + ", " + percent((double) repository.getValue() / row.locations())
                + "</td></tr>";
    }

    static String name(Row row, Set<String> pages, String up) {
        return row.blobShas().stream().filter(pages::contains).findFirst()
                .map(sha -> "<a href=\"" + up + "dockerfiles/" + escape(sha) + ".html\">" + escape(row.name()) + "</a>")
                .orElseGet(() -> escape(row.name()) + "<br><span class=\"note\">"
                        + escape(row.where().getFirst().repository() + ": " + row.where().getFirst().path())
                        + "</span>");
    }

    static String chips(Map<String, Integer> organisations) {
        return organisations.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(CHIPS)
                .map(e -> "<span class=\"chip\">" + escape(e.getKey()) + " " + count(e.getValue()) + "</span>")
                .collect(Collectors.joining(" "))
                + (organisations.size() > CHIPS ? " and " + (organisations.size() - CHIPS) + " more" : "");
    }

    static String apart(Map<String, Apart> apart) {
        return apart.isEmpty() ? "" : "<p class=\"note\">Not ranked: " + apart.entrySet().stream()
                .map(e -> escape(e.getKey()) + ": " + plural(e.getValue().locations(), "location", "locations")
                        + " in " + plural(e.getValue().files(), "file", "files"))
                .collect(Collectors.joining("; ")) + ".</p>";
    }
}
