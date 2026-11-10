package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class RepositoryPage {

    private static final Comparator<ActionPlanView.PlannedAction> MOST_URGENT = Comparator
            .comparingInt((ActionPlanView.PlannedAction a) -> ActionDrilldown.rank(a.severity()))
            .thenComparing(ActionPlanView.PlannedAction::check);

    private RepositoryPage() {
    }

    static Map<String, String> pages(List<ActionPlanView.PlannedAction> actions, Set<String> catalogued) {
        Map<String, String> pages = new LinkedHashMap<>();
        actions.stream().flatMap(a -> a.where().stream().map(ActionPlanView.Where::repository)).distinct()
                .forEach(repository -> pages.put(path(repository), Page.render(repository, "../",
                        body(repository, actions, catalogued))));
        return pages;
    }

    static String path(String repository) {
        return "repositories/" + Drilldowns.slug(repository) + ".html";
    }

    static String body(String repository, List<ActionPlanView.PlannedAction> actions, Set<String> catalogued) {
        List<ActionPlanView.PlannedAction> here = actions.stream()
                .filter(a -> a.where().stream().anyMatch(w -> w.repository().equals(repository))).toList();
        return "<p>" + count(here.size()) + " actions, most urgent first.</p>" + Html.scrollTable("<th>Severity</th>"
                + "<th>Check</th><th>What to change</th><th>Dockerfile</th>", here.stream().sorted(MOST_URGENT)
                .map(action -> row(action, repository, catalogued)).collect(Collectors.joining()));
    }

    static String row(ActionPlanView.PlannedAction action, String repository, Set<String> catalogued) {
        ActionPlanView.Where where = action.where().stream().filter(w -> w.repository().equals(repository))
                .findFirst().orElseThrow();
        return "<tr><td>" + Html.lowerCase(action.severity()) + "</td><td>"
                + ActionDrilldown.checkLink("../", action.check()) + "</td><td>" + escape(action.what()) + "</td><td>"
                + Html.dockerfileLink("../", action.blobSha(), where.path(), catalogued) + "</td></tr>";
    }
}
