package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import io.github.fiftieshousewife.dockerfileinventory.report.ArchetypeRows.Row;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class Pages {

    static final int PER_PAGE = 250;

    private Pages() {
    }

    static Map<String, String> all(CatalogueView view, ArchetypeRows rows, Optional<ActionPlanView> plan) {
        List<CatalogueEntry> entries = view.entries();
        Map<String, List<CatalogueEntry>> byOrganisation = entries.stream()
                .collect(Collectors.groupingBy(CatalogueView::organisation, TreeMap::new,
                        Collectors.toList()));
        Map<String, String> pages = new LinkedHashMap<>();
        Set<String> catalogued = entries.stream().map(CatalogueEntry::blobSha).collect(Collectors.toSet());
        List<String> organisations = List.copyOf(byOrganisation.keySet());
        pages.put("index.html", Page.render("Dockerfile estate", "", Tabs.render(organisations, null, "")
                + plan.map(p -> ActionPlanPage.lead(p)
                + "<p><a href=\"openshift.html\">Action plan for OpenShift</a></p>").orElse("")
                + OverviewPage.body(EstateFigures.of(entries), organisations, "", "lists/")));
        pages.putAll(Drilldowns.pages("", Breakdowns.of(entries)));
        pages.put("unparsed.html", Page.render("Did not parse", "", UnparsedPage.body(entries.stream()
                .filter(entry -> entry.parseError() != null).toList())));
        plan.ifPresent(p -> pages.put("openshift.html", Page.render("Action plan for OpenShift", "",
                ActionPlanPage.body(p, catalogued))));
        byOrganisation.forEach((organisation, within) -> pages.put("orgs/" + organisation + ".html",
                Page.render(organisation, "../", Tabs.render(organisations, organisation, "../")
                        + OverviewPage.body(EstateFigures.of(within), List.of(), "../", organisation + "/lists/"))));
        byOrganisation.forEach((organisation, within) -> pages.putAll(Drilldowns.pages("orgs/" + organisation + "/",
                Breakdowns.of(within))));
        pages.put("hierarchy.html", Page.render("Hierarchy", "", HierarchyPage.body(entries)));
        pages.put("blocked.html", Page.render("Blocked", "", BlockedPage.body(entries)));
        pages.putAll(ranking(rows, catalogued));
        Map<String, List<ActionPlanView.PlannedWorkload>> running = plan.map(p -> Deployments.byDockerfile(
                p.workloads())).orElse(Map.of());
        entries.forEach(entry -> pages.put("dockerfiles/" + entry.blobSha() + ".html", Page.render(
                entry.locations().getFirst().repository() + "/" + entry.locations().getFirst().path(), "../",
                DockerfilePage.body(entry) + Deployments.section(entry.blobSha(),
                        running.getOrDefault(entry.blobSha(), List.of())))));
        return pages;
    }

    static Map<String, String> ranking(ArchetypeRows archetypes, Set<String> catalogued) {
        List<Row> rows = archetypes.rows();
        int count = Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE);
        return IntStream.rangeClosed(1, count).boxed().collect(Collectors.toMap(Pages::rankingPath,
                page -> Page.render("Archetypes", "", intro(archetypes) + pager(page, rows.size(), PER_PAGE)
                        + ArchetypeTable.render(rows, (page - 1) * PER_PAGE, page * PER_PAGE, catalogued, "")
                        + pager(page, rows.size(), PER_PAGE)),
                (a, b) -> a, LinkedHashMap::new));
    }

    static String intro(ArchetypeRows rows) {
        return "<p class=\"lead\">" + escape(About.TEXT.get("Archetypes")) + "</p><p>" + count(rows.rows().size())
                + " archetypes, largest first; " + count(rows.k80()) + " cover 80% of locations. Each links to the "
                + "page of one of its Dockerfiles, which lists its near-duplicates.</p>"
                + ArchetypeTable.apart(rows.apart());
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
