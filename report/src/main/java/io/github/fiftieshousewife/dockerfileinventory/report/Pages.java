package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import io.github.fiftieshousewife.dockerfileinventory.report.ArchetypeRows.Row;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Every HTML page of the report, by its path under OUT_DIR.
final class Pages {

    static final int PER_PAGE = 250;

    private Pages() {
    }

    static Map<String, String> all(Report r) {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("index.html", Page.render("Dockerfile estate", "", SummaryPage.estate(r)));
        r.organisations().forEach(f -> pages.put(OrganisationsSection.path(f.name()),
                Page.render(f.name(), "../", SummaryPage.organisation(r, f))));
        pages.putAll(ranking(r));
        r.rows().rows().stream().filter(row -> r.pages().contains(row.archetype().rank()))
                .forEach(row -> pages.put(ArchetypeTable.page(row.archetype().rank()), Page.render(
                        "Archetype " + row.rank() + ": " + row.name(), "../",
                        ArchetypePage.body(row, r.locations(), r.discoveryDir()))));
        pages.put("bases.html", Page.render("Bases", "", BasesPage.body(r.bases())));
        pages.put("lineage.html", Page.render("Lineage", "", LineagePage.body(r.lineage())));
        return pages;
    }

    static Map<String, String> ranking(Report r) {
        List<Row> rows = r.rows().rows();
        int count = Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE);
        return IntStream.rangeClosed(1, count).boxed().collect(Collectors.toMap(Pages::rankingPath,
                page -> Page.render("Archetypes", "", intro(r) + pager(page, rows.size(), PER_PAGE)
                        + ArchetypeTable.render(rows, (page - 1) * PER_PAGE, page * PER_PAGE, r.pages(), "")
                        + pager(page, rows.size(), PER_PAGE)),
                (a, b) -> a, LinkedHashMap::new));
    }

    static String intro(Report r) {
        return "<p class=\"lead\">" + escape(About.TEXT.get("Archetypes")) + "</p><p>" + count(r.rows().rows().size())
                + " archetypes, largest first; " + count(r.rows().k80()) + " cover 80% of locations. An archetype at "
                + "one location names it instead of linking to a page.</p>" + ArchetypeTable.apart(r.rows().apart());
    }

    static String rankingPath(int page) {
        return page == 1 ? "archetypes.html" : "archetypes-" + page + ".html";
    }

    static String pager(int page, int rows, int perPage) {
        int pages = (rows + perPage - 1) / perPage;
        return pages <= 1 ? "" : "<nav class=\"pager\" aria-label=\"Ranking pages\">" + IntStream.rangeClosed(1, pages)
                .mapToObj(p -> {
                    String range = count((p - 1) * perPage + 1) + " to " + count(Math.min(rows, p * perPage));
                    return p == page ? "<strong>" + range + "</strong>"
                            : "<a href=\"" + rankingPath(p) + "\">" + range + "</a>";
                })
                .collect(Collectors.joining()) + "</nav>";
    }
}
