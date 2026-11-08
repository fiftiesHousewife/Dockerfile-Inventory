package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// The validator's plan: how the images fare under the profile, then the actions by urgency, then per repository.
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
                + count(plan.notRunnable()) + " not judged (metadata-only bundles).</p>";
    }

    static String body(ActionPlanView view, Set<String> catalogued) {
        int hidden = view.actions().size() - SHOWN;
        return lead(view) + "<p class=\"note\">" + escape(view.plan().describes()) + "</p>"
                + "<h2>Actions, most urgent first</h2><div class=\"scroll\"><table><tr><th>Severity</th><th>Check</th>"
                + "<th>What to change</th><th>Why</th><th>Dockerfile</th><th class=\"num\">Locations</th></tr>"
                + view.actions().stream().limit(SHOWN).map(a -> row(a, catalogued)).collect(Collectors.joining())
                + "</table></div>"
                + (hidden > 0 ? "<p>and " + count(hidden) + " more in the validator's actions.jsonl.</p>" : "")
                + "<h2>By repository</h2><div class=\"scroll\"><table><tr><th>Repository</th><th class=\"num\">Actions"
                + "</th></tr>" + view.plan().repositories().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> "<tr><td>" + escape(e.getKey()) + "</td><td class=\"num\">" + count(e.getValue())
                        + "</td></tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }

    private static String row(ActionPlanView.PlannedAction action, Set<String> catalogued) {
        ActionPlanView.Where first = action.where().isEmpty() ? null : action.where().getFirst();
        String name = first == null ? action.blobSha() : first.repository() + " " + first.path();
        return "<tr><td>" + escape(action.severity().toLowerCase(Locale.ROOT)) + " ("
                + escape(action.certainty().toLowerCase(Locale.ROOT)) + ")</td><td>" + escape(action.check())
                + "</td><td>" + escape(action.what()) + "</td><td>" + escape(String.join("; ", action.because()))
                + "</td><td>" + (catalogued.contains(action.blobSha()) ? "<a href=\"dockerfiles/"
                + escape(action.blobSha()) + ".html\">" + escape(name) + "</a>" : escape(name)) + "</td>"
                + "<td class=\"num\">" + count(action.where().size()) + "</td></tr>";
    }
}
