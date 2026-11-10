package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// A page for each row of the summary, listing the Dockerfiles its count counts.
final class Drilldowns {

    private Drilldowns() {
    }

    static Map<String, String> pages(String prefix, Map<String, Map<String, List<CatalogueEntry>>> groups) {
        Map<String, String> pages = new LinkedHashMap<>();
        groups.forEach((section, rows) -> rows.forEach((label, entries) -> {
            String path = prefix + path(section, label);
            String up = "../".repeat((int) path.chars().filter(c -> c == '/').count());
            pages.put(path, Page.render(label, up, list(entries, up)));
        }));
        return pages;
    }

    static String path(String section, String label) {
        return "lists/" + section + "/" + slug(label) + ".html";
    }

    static String slug(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    static String list(List<CatalogueEntry> entries, String up) {
        return "<p>" + count(entries.size()) + " Dockerfiles.</p><div class=\"scroll\"><table><tr><th>Dockerfile"
                + "</th><th>Organisation</th><th class=\"num\">Locations</th></tr>" + entries.stream()
                .sorted(java.util.Comparator.comparing(CatalogueView::organisation)
                        .thenComparing(e -> e.locations().getFirst().repository()))
                .map(entry -> "<tr><td><a href=\"" + up + "dockerfiles/" + escape(entry.blobSha()) + ".html\">"
                        + escape(entry.locations().getFirst().repository() + " " + entry.locations().getFirst()
                        .path()) + "</a></td><td>" + escape(CatalogueView.organisation(entry))
                        + "</td><td class=\"num\">" + count(entry.locations().size()) + "</td></tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }
}
