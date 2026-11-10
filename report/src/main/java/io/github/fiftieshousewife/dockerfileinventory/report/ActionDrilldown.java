package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// The action plan from the top down: each check with its counts, a check's fixes, a fix's Dockerfiles; and each
// repository's actions.
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
                .entrySet().stream().map(e -> new CheckRow(e.getKey(), worst(e.getValue()), CheckNames.of(e.getKey()),
                        (int) e.getValue().stream().map(ActionPlanView.PlannedAction::blobSha).distinct().count(),
                        fixes.getOrDefault(e.getKey(), List.of()).size(),
                        fixes.getOrDefault(e.getKey(), List.of()).stream()
                                .mapToInt(ActionPlanView.PlannedFix::locations).sum()))
                .sorted(Comparator.comparingInt((CheckRow row) -> WORST_FIRST.indexOf(row.severity()))
                        .thenComparing(Comparator.comparingInt(CheckRow::dockerfiles).reversed())
                        .thenComparing(CheckRow::check))
                .toList();
    }

    static String worst(List<ActionPlanView.PlannedAction> actions) {
        return actions.stream().map(ActionPlanView.PlannedAction::severity)
                .min(Comparator.comparingInt(WORST_FIRST::indexOf)).orElse("INFORMATION");
    }

    static String checkTable(List<CheckRow> rows, String up) {
        return "<div class=\"scroll\"><table><tr><th>Severity</th><th>Check</th><th>What</th>"
                + "<th class=\"num\">Dockerfiles</th><th class=\"num\">Fixes</th><th class=\"num\">Locations</th></tr>"
                + rows.stream().map(row -> "<tr><td>" + escape(row.severity().toLowerCase(Locale.ROOT))
                        + "</td><td><a href=\"" + up + "checks/" + escape(row.check()) + ".html\">"
                        + escape(row.check()) + "</a></td><td>" + escape(row.name()) + "</td><td class=\"num\">"
                        + count(row.dockerfiles()) + "</td><td class=\"num\">" + count(row.fixes())
                        + "</td><td class=\"num\">" + count(row.locations()) + "</td></tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }

    static Map<String, String> pages(ActionPlanView view, Set<String> catalogued) {
        Map<String, ActionPlanView.Where> places = ActionPlanPage.firstPlaces(view.actions());
        Map<String, String> pages = new LinkedHashMap<>();
        view.fixes().stream().collect(Collectors.groupingBy(ActionPlanView.PlannedFix::check, LinkedHashMap::new,
                Collectors.toList())).forEach((check, fixes) -> pages.put("checks/" + check + ".html",
                Page.render(check + ": " + CheckNames.of(check), "../", checkPage(fixes, places, catalogued))));
        view.fixes().stream().filter(fix -> fix.copies().size() + fix.inheriting().size() > 1)
                .forEach(fix -> pages.put(fixPath(fix), Page.render(fix.check() + ": " + CheckNames.of(fix.check()),
                        "../", fixPage(fix, places, catalogued))));
        view.actions().stream().flatMap(a -> a.where().stream().map(ActionPlanView.Where::repository)).distinct()
                .forEach(repository -> pages.put(repositoryPath(repository), Page.render(repository, "../",
                        repositoryPage(repository, view.actions(), catalogued))));
        return pages;
    }

    static String fixPath(ActionPlanView.PlannedFix fix) {
        return "fixes/" + fix.check() + "-" + fix.representative() + ".html";
    }

    static String repositoryPath(String repository) {
        return "repositories/" + Drilldowns.slug(repository) + ".html";
    }

    static String checkPage(List<ActionPlanView.PlannedFix> fixes, Map<String, ActionPlanView.Where> places,
                            Set<String> catalogued) {
        return "<p>" + escape(fixes.getFirst().what()) + ".</p><p>" + count(fixes.size()) + " fixes, largest "
                + "reach first. A fix is one change to an archetype's template, reaching its copies and the "
                + "Dockerfiles built on them.</p><div class=\"scroll\"><table><tr><th>Dockerfile to change</th>"
                + "<th>Why</th><th class=\"num\">Dockerfiles</th><th class=\"num\">Locations</th></tr>"
                + fixes.stream().sorted(Comparator.comparingInt(ActionPlanView.PlannedFix::locations).reversed())
                .map(fix -> "<tr><td>" + ActionPlanPage.dockerfile(fix.representative(),
                        places.get(fix.representative()), catalogued).replace("href=\"dockerfiles/",
                        "href=\"../dockerfiles/") + "</td><td>" + escape(String.join("; ", fix.because()))
                        + "</td><td class=\"num\">" + reach(fix) + "</td><td class=\"num\">"
                        + count(fix.locations()) + "</td></tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }

    static String reach(ActionPlanView.PlannedFix fix) {
        int dockerfiles = fix.copies().size() + fix.inheriting().size();
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
        return "<ul>" + shas.stream().map(sha -> "<li>" + ActionPlanPage.dockerfile(sha, places.get(sha),
                        catalogued).replace("href=\"dockerfiles/", "href=\"../dockerfiles/") + "</li>")
                .collect(Collectors.joining()) + "</ul>";
    }

    static String repositoryPage(String repository, List<ActionPlanView.PlannedAction> actions,
                                 Set<String> catalogued) {
        List<ActionPlanView.PlannedAction> here = actions.stream()
                .filter(a -> a.where().stream().anyMatch(w -> w.repository().equals(repository))).toList();
        return "<p>" + count(here.size()) + " actions, most urgent first.</p><div class=\"scroll\"><table><tr>"
                + "<th>Severity</th><th>Check</th><th>What to change</th><th>Dockerfile</th></tr>" + here.stream()
                .sorted(Comparator.comparingInt((ActionPlanView.PlannedAction a) -> WORST_FIRST.indexOf(a.severity()))
                        .thenComparing(ActionPlanView.PlannedAction::check))
                .map(a -> "<tr><td>" + escape(a.severity().toLowerCase(Locale.ROOT)) + "</td><td><a href=\"../checks/"
                        + escape(a.check()) + ".html\">" + escape(a.check()) + "</a></td><td>" + escape(a.what())
                        + "</td><td>" + Stream.of(a.where().stream().filter(w -> w.repository().equals(repository))
                        .findFirst().orElseThrow()).map(w -> catalogued.contains(a.blobSha())
                        ? "<a href=\"../dockerfiles/" + escape(a.blobSha()) + ".html\">" + escape(w.path()) + "</a>"
                        : escape(w.path())).findFirst().orElseThrow() + "</td></tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }
}
