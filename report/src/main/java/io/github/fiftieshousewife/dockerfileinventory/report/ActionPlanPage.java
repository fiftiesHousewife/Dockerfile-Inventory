package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ActionPlanPage {

    static final int SHOWN = 250;

    private ActionPlanPage() {
    }

    static String lead(ActionPlanView view) {
        ActionPlanView.Plan plan = view.plan();
        Map<String, Integer> verdicts = plan.verdicts();
        return "<p>Under <code>" + escape(plan.profile()) + "</code>, " + count(verdicts.getOrDefault("breaks", 0))
                + " of " + count(plan.images()) + " images would break, " + count(verdicts.getOrDefault("drift", 0))
                + " drift, " + count(verdicts.getOrDefault("information", 0)) + " need only the deployment to supply "
                + "something, and " + count(verdicts.getOrDefault("runs", 0)) + " run as they are; "
                + count(plan.notRunnable()) + " not judged (metadata-only bundles). Their " + count(plan.actions())
                + " actions come to " + count(plan.fixes()) + " fixes: the same change to an archetype's copies is one "
                + "fix to its template.</p>" + workloads(plan);
    }

    static final Map<String, String> WORKLOAD_VERDICTS = Map.of("refused", " would be refused admission",
            "breaks", " would break", "drift", " drift", "information", " need only the deployment to supply "
                    + "something", "runs", " run as they are");

    static String workloads(ActionPlanView.Plan plan) {
        if (plan.workloads() == null) {
            return "";
        }
        List<String> parts = plan.workloadVerdicts().entrySet().stream()
                .map(e -> count(e.getValue()) + WORKLOAD_VERDICTS.get(e.getKey())).toList();
        return "<p>Of " + count(plan.workloads()) + " workloads, " + inWords(parts) + ".</p>";
    }

    static String inWords(List<String> parts) {
        return parts.size() == 1 ? parts.getFirst()
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    static String body(ActionPlanView view, Set<String> catalogued) {
        Set<String> withPages = view.actions().stream().flatMap(a -> a.where().stream()
                .map(ActionPlanView.Where::repository)).collect(Collectors.toSet());
        return lead(view) + "<p class=\"note\">" + escape(view.plan().describes()) + "</p>"
                + "<h2>What to fix, by check</h2><p>Each check, worst first; each links to its fixes, and a fix to the "
                + "Dockerfiles it changes.</p>" + ActionDrilldown.checkTable(ActionDrilldown.byCheck(view), "")
                + WorkloadTable.render(view.workloads(), catalogued)
                + repositories(view.plan().repositories(), withPages);
    }

    static Map<String, ActionPlanView.Where> firstPlaces(List<ActionPlanView.PlannedAction> actions) {
        Map<String, ActionPlanView.Where> places = new HashMap<>();
        actions.forEach(a -> a.where().stream().findFirst().ifPresent(w -> places.putIfAbsent(a.blobSha(), w)));
        return places;
    }

    static String repositories(Map<String, Integer> repositories, Set<String> withPages) {
        return "<h2>By repository</h2><div class=\"scroll\"><table><tr><th>Repository</th><th class=\"num\">Actions"
                + "</th></tr>" + repositories.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> "<tr><td>" + (withPages.contains(e.getKey()) ? "<a href=\""
                        + ActionDrilldown.repositoryPath(e.getKey()) + "\">" + escape(e.getKey()) + "</a>"
                        : escape(e.getKey())) + "</td><td class=\"num\">" + count(e.getValue())
                        + "</td></tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }

    static String dockerfile(String sha, ActionPlanView.Where place, Set<String> catalogued) {
        String name = place == null ? sha : place.repository() + " " + place.path();
        return catalogued.contains(sha) ? "<a href=\"dockerfiles/" + escape(sha) + ".html\">" + escape(name) + "</a>"
                : escape(name);
    }
}
