package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ActionDrilldown {

    static final List<String> WORST_FIRST = List.of("REFUSED", "BREAKS", "DRIFT", "INFORMATION");

    record CheckRow(String check, String severity, String name, int dockerfiles, int fixes, int locations) {
    }

    private ActionDrilldown() {
    }

    static List<CheckRow> byCheck(ActionPlanView view) {
        Map<String, List<ActionPlanView.PlannedFix>> fixes = view.fixes().stream()
                .collect(Collectors.groupingBy(ActionPlanView.PlannedFix::check));
        return view.actions().stream().collect(Collectors.groupingBy(ActionPlanView.PlannedAction::check,
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> checkRow(e.getKey(), e.getValue(),
                        fixes.getOrDefault(e.getKey(), List.of())))
                .sorted(Comparator.comparingInt((CheckRow row) -> WORST_FIRST.indexOf(row.severity()))
                        .thenComparing(Comparator.comparingInt(CheckRow::dockerfiles).reversed())
                        .thenComparing(CheckRow::check))
                .toList();
    }

    static CheckRow checkRow(String check, List<ActionPlanView.PlannedAction> actions,
                             List<ActionPlanView.PlannedFix> fixes) {
        return new CheckRow(check, worst(actions), CheckNames.of(check),
                (int) actions.stream().map(ActionPlanView.PlannedAction::blobSha).distinct().count(), fixes.size(),
                fixes.stream().mapToInt(ActionPlanView.PlannedFix::locations).sum());
    }

    static String worst(List<ActionPlanView.PlannedAction> actions) {
        return actions.stream().map(ActionPlanView.PlannedAction::severity)
                .min(Comparator.comparingInt(WORST_FIRST::indexOf)).orElse("INFORMATION");
    }

    static String checkTable(List<CheckRow> rows, String up) {
        return Html.scrollTable("<th>Severity</th><th>Check</th><th>What</th><th class=\"num\">Dockerfiles</th>"
                + "<th class=\"num\">Fixes</th><th class=\"num\">Locations</th>", rows.stream()
                .map(row -> "<tr><td>" + Html.lowerCase(row.severity()) + "</td><td>" + checkLink(up, row.check())
                        + "</td><td>" + escape(row.name()) + "</td><td class=\"num\">" + count(row.dockerfiles())
                        + "</td><td class=\"num\">" + count(row.fixes()) + "</td><td class=\"num\">"
                        + count(row.locations()) + "</td></tr>")
                .collect(Collectors.joining()));
    }

    static String checkLink(String up, String check) {
        return "<a href=\"" + up + "checks/" + escape(check) + ".html\">" + escape(check) + "</a>";
    }

    static Map<String, String> pages(ActionPlanView view, Set<String> catalogued) {
        Map<String, ActionPlanView.Where> places = ActionPlanPage.firstPlaces(view.actions());
        Map<String, String> pages = new LinkedHashMap<>();
        view.fixes().stream().collect(Collectors.groupingBy(ActionPlanView.PlannedFix::check, LinkedHashMap::new,
                Collectors.toList())).forEach((check, fixes) -> pages.put("checks/" + check + ".html",
                Page.render(check + ": " + CheckNames.of(check), "../", checkPage(fixes, places, catalogued))));
        view.fixes().stream().filter(fix -> reached(fix) > 1)
                .forEach(fix -> pages.put(fixPath(fix), Page.render(fix.check() + ": " + CheckNames.of(fix.check()),
                        "../", fixPage(fix, places, catalogued))));
        pages.putAll(RepositoryPage.pages(view.actions(), catalogued));
        return pages;
    }

    static String fixPath(ActionPlanView.PlannedFix fix) {
        return "fixes/" + fix.check() + "-" + fix.representative() + ".html";
    }

    static String checkPage(List<ActionPlanView.PlannedFix> fixes, Map<String, ActionPlanView.Where> places,
                            Set<String> catalogued) {
        return "<p>" + escape(fixes.getFirst().what()) + ".</p><p>" + count(fixes.size()) + " fixes, largest "
                + "reach first. A fix is one change to an archetype's template, reaching its copies and the "
                + "Dockerfiles built on them.</p>" + Html.scrollTable("<th>Dockerfile to change</th><th>Why</th>"
                + "<th class=\"num\">Dockerfiles</th><th class=\"num\">Locations</th>", fixes.stream()
                .sorted(Comparator.comparingInt(ActionPlanView.PlannedFix::locations).reversed())
                .map(fix -> "<tr><td>" + dockerfile(fix.representative(), places, catalogued) + "</td><td>"
                        + escape(String.join("; ", fix.because())) + "</td><td class=\"num\">" + reach(fix)
                        + "</td><td class=\"num\">" + count(fix.locations()) + "</td></tr>")
                .collect(Collectors.joining()));
    }

    static int reached(ActionPlanView.PlannedFix fix) {
        return fix.copies().size() + fix.inheriting().size();
    }

    static String reach(ActionPlanView.PlannedFix fix) {
        int dockerfiles = reached(fix);
        return dockerfiles > 1 ? "<a href=\"../" + fixPath(fix) + "\">" + count(dockerfiles) + "</a>"
                : count(dockerfiles);
    }

    static String fixPage(ActionPlanView.PlannedFix fix, Map<String, ActionPlanView.Where> places,
                          Set<String> catalogued) {
        return "<p>" + escape(fix.what()) + ".</p><p>Why: " + escape(String.join("; ", fix.because())) + "</p>"
                + "<h2>The copies</h2>" + list(fix.copies(), places, catalogued)
                + (fix.inheriting().isEmpty() ? "" : "<h2>Dockerfiles built on them</h2><p>They keep what the fix "
                + "changes, so it reaches them too.</p>" + list(fix.inheriting(), places, catalogued));
    }

    static String list(List<String> shas, Map<String, ActionPlanView.Where> places, Set<String> catalogued) {
        return "<ul>" + shas.stream().map(sha -> "<li>" + dockerfile(sha, places, catalogued) + "</li>")
                .collect(Collectors.joining()) + "</ul>";
    }

    static String dockerfile(String sha, Map<String, ActionPlanView.Where> places, Set<String> catalogued) {
        ActionPlanView.Where place = places.get(sha);
        return Html.dockerfileLink("../", sha, place == null ? sha : place.repository() + " " + place.path(),
                catalogued);
    }
}
