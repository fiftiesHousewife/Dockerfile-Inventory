package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// The validator's plan: how the images fare under the profile, then the fixes by urgency, then per repository.
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
                + "fix to its template.</p>";
    }

    static String body(ActionPlanView view, Set<String> catalogued) {
        int hidden = view.fixes().size() - SHOWN;
        Map<String, ActionPlanView.Where> places = new HashMap<>();
        view.actions().forEach(a -> a.where().stream().findFirst().ifPresent(w -> places.putIfAbsent(a.blobSha(), w)));
        return lead(view) + "<p class=\"note\">" + escape(view.plan().describes()) + "</p>"
                + "<h2>Fixes, most urgent first</h2><div class=\"scroll\"><table><tr><th>Severity</th><th>Check</th>"
                + "<th>What to change</th><th>Why</th><th>Dockerfile to change</th><th class=\"num\">Copies</th>"
                + "<th class=\"num\">Built on them</th><th class=\"num\">Locations</th></tr>"
                + view.fixes().stream().limit(SHOWN).map(f -> row(f, places.get(f.representative()), catalogued))
                .collect(Collectors.joining())
                + "</table></div>"
                + (hidden > 0 ? "<p>and " + count(hidden) + " more in the validator's fixes.jsonl.</p>" : "")
                + "<h2>By repository</h2><div class=\"scroll\"><table><tr><th>Repository</th><th class=\"num\">Actions"
                + "</th></tr>" + view.plan().repositories().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> "<tr><td>" + escape(e.getKey()) + "</td><td class=\"num\">" + count(e.getValue())
                        + "</td></tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }

    private static String row(ActionPlanView.PlannedFix fix, ActionPlanView.Where place, Set<String> catalogued) {
        String sha = fix.representative();
        String name = place == null ? sha : place.repository() + " " + place.path();
        return "<tr><td>" + escape(fix.severity().toLowerCase(Locale.ROOT)) + " ("
                + escape(fix.certainty().toLowerCase(Locale.ROOT)) + ")</td><td>" + escape(fix.check())
                + "</td><td>" + escape(fix.what()) + "</td><td>" + escape(String.join("; ", fix.because()))
                + "</td><td>" + (catalogued.contains(sha) ? "<a href=\"dockerfiles/" + escape(sha) + ".html\">"
                + escape(name) + "</a>" : escape(name)) + "</td><td class=\"num\">" + count(fix.copies().size())
                + "</td><td class=\"num\">" + count(fix.inheriting().size()) + "</td><td class=\"num\">"
                + count(fix.locations()) + "</td></tr>";
    }
}
