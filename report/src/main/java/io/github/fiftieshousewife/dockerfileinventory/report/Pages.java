package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.LinkedHashMap;
import java.util.Map;

// Every HTML page of the report, by its path under OUT_DIR.
final class Pages {

    private Pages() {
    }

    static Map<String, String> all(Report r) {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("index.html", Page.render("Dockerfile estate", "", SummaryPage.estate(r)));
        r.organisations().forEach(f -> pages.put(OrganisationsSection.path(f.name()),
                Page.render(f.name(), "../", SummaryPage.organisation(r, f))));
        pages.put("archetypes.html", Page.render("Archetypes", "", archetypes(r)));
        r.rows().rows().stream().filter(row -> r.pages().contains(row.archetype().rank()))
                .forEach(row -> pages.put(ArchetypeTable.page(row.archetype().rank()), Page.render(
                        "Archetype " + row.rank() + ": " + row.name(), "../",
                        ArchetypePage.body(row, r.locations(), r.discoveryDir()))));
        pages.put("bases.html", Page.render("Bases", "", BasesPage.body(r.bases())));
        pages.put("lineage.html", Page.render("Lineage", "", LineagePage.body(r.lineage())));
        return pages;
    }

    static String archetypes(Report r) {
        return "<p class=\"lead\">" + escape(About.TEXT.get("Archetypes")) + "</p><p>" + count(r.rows().rows().size())
                + " archetypes, largest first; " + count(r.rows().k80()) + " cover 80% of locations. An archetype at "
                + "one location names it instead of linking to a page.</p>"
                + ArchetypeTable.apart(r.rows().apart())
                + ArchetypeTable.render(r.rows().rows(), Integer.MAX_VALUE, r.pages(), "");
    }
}
