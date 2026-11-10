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

    static final int TOP_CHECKS = 10;

    private Pages() {
    }

    static Map<String, String> all(CatalogueView view, ArchetypeRows rows, Optional<ActionPlanView> plan) {
        List<CatalogueEntry> entries = view.entries();
        Map<String, List<CatalogueEntry>> byOrganisation = entries.stream()
                .collect(Collectors.groupingBy(CatalogueView::organisation, TreeMap::new, Collectors.toList()));
        List<String> organisations = List.copyOf(byOrganisation.keySet());
        Set<String> catalogued = entries.stream().map(CatalogueEntry::blobSha).collect(Collectors.toSet());
        Map<String, String> programs = programs(entries);
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("index.html", index(entries, organisations, plan));
        pages.putAll(Drilldowns.pages("", Breakdowns.of(entries)));
        pages.put("unparsed.html", Page.render("Did not parse", "", UnparsedPage.body(entries.stream()
                .filter(entry -> entry.parseError() != null).toList())));
        plan.ifPresent(p -> pages.putAll(actionPlan(p, catalogued)));
        pages.putAll(organisationPages(byOrganisation));
        pages.put("hierarchy.html", Page.render("Hierarchy", "", HierarchyPage.body(entries)));
        pages.put("blocked.html", Page.render("Blocked", "", BlockedPage.body(entries)));
        pages.putAll(ranking(rows, catalogued, programs));
        pages.putAll(dockerfilePages(entries, programs, plan.map(p -> Deployments.byDockerfile(p.workloads()))
                .orElse(Map.of())));
        return pages;
    }

    static Map<String, String> programs(List<CatalogueEntry> entries) {
        return entries.stream().filter(entry -> Programs.of(entry) != null)
                .collect(Collectors.toMap(CatalogueEntry::blobSha, Programs::of, (a, b) -> a));
    }

    static String index(List<CatalogueEntry> entries, List<String> organisations, Optional<ActionPlanView> plan) {
        return Page.render("Dockerfile estate", "", Tabs.render(organisations, null, "")
                + plan.map(Pages::whatToFixFirst).orElse("")
                + OverviewPage.body(EstateFigures.of(entries), organisations, "", "lists/"));
    }

    static String whatToFixFirst(ActionPlanView plan) {
        return ActionPlanPage.lead(plan)
                + "<p><a href=\"openshift.html\">Action plan for OpenShift</a></p><h2>What to fix first</h2>"
                + ActionDrilldown.checkTable(ActionDrilldown.byCheck(plan).stream().limit(TOP_CHECKS).toList(), "")
                + "<p><a href=\"openshift.html\">Every check</a></p>";
    }

    static Map<String, String> actionPlan(ActionPlanView plan, Set<String> catalogued) {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("openshift.html", Page.render("Action plan for OpenShift", "",
                ActionPlanPage.body(plan, catalogued)));
        pages.putAll(ActionDrilldown.pages(plan, catalogued));
        return pages;
    }

    static Map<String, String> organisationPages(Map<String, List<CatalogueEntry>> byOrganisation) {
        List<String> organisations = List.copyOf(byOrganisation.keySet());
        Map<String, String> pages = new LinkedHashMap<>();
        byOrganisation.forEach((organisation, within) -> pages.put("orgs/" + organisation + ".html",
                Page.render(organisation, "../", Tabs.render(organisations, organisation, "../")
                        + OverviewPage.body(EstateFigures.of(within), List.of(), "../", organisation + "/lists/"))));
        byOrganisation.forEach((organisation, within) -> pages.putAll(Drilldowns.pages("orgs/" + organisation + "/",
                Breakdowns.of(within))));
        return pages;
    }

    static Map<String, String> dockerfilePages(List<CatalogueEntry> entries, Map<String, String> programs,
                                               Map<String, List<ActionPlanView.PlannedWorkload>> running) {
        return entries.stream().collect(Collectors.toMap(entry -> "dockerfiles/" + entry.blobSha() + ".html",
                entry -> dockerfilePage(entry, programs, running.getOrDefault(entry.blobSha(), List.of())),
                (a, b) -> b, LinkedHashMap::new));
    }

    static String dockerfilePage(CatalogueEntry entry, Map<String, String> programs,
                                 List<ActionPlanView.PlannedWorkload> running) {
        return Page.render(entry.locations().getFirst().repository() + "/" + entry.locations().getFirst().path(),
                "../", DockerfilePage.body(entry, programs) + Deployments.section(entry.blobSha(), running));
    }

    static Map<String, String> ranking(ArchetypeRows archetypes, Set<String> catalogued,
                                       Map<String, String> programs) {
        List<Row> rows = archetypes.rows();
        int count = Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE);
        return IntStream.rangeClosed(1, count).boxed().collect(Collectors.toMap(Pages::rankingPath,
                page -> Page.render("Archetypes", "", intro(archetypes) + pager(page, rows.size(), PER_PAGE)
                        + ArchetypeTable.render(rows, (page - 1) * PER_PAGE, page * PER_PAGE, catalogued, programs, "")
                        + pager(page, rows.size(), PER_PAGE)),
                (a, b) -> a, LinkedHashMap::new));
    }

    static String intro(ArchetypeRows rows) {
        return "<p class=\"lead\">" + escape(About.TEXT.get("Archetypes")) + "</p><p>" + count(rows.rows().size())
                + " archetypes, largest first; " + count(rows.k80()) + " cover 80% of locations. Each links to the "
                + "page of one of its Dockerfiles, which lists the others built by the same recipe: an archetype is a "
                + "recipe, and Programs counts the different things its Dockerfiles start.</p>"
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
