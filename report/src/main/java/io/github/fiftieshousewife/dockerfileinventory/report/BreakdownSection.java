package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.folded;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.h2;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.row;

import io.github.fiftieshousewife.dockerfileinventory.report.StackedChart.Segment;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class BreakdownSection {

    // Fixed, so a manager keeps its colour from run to run; any other manager follows, alphabetically.
    private static final List<String> MANAGER_ORDER =
            List.of("dnf", "microdnf", "yum", "apt-get", "apt", "apk", "zypper");
    private static final List<String> USER_ORDER = List.of("ROOT", "NUMERIC", "NAMED", "UNRESOLVED");

    private BreakdownSection() {
    }

    static String render(Summary summary) {
        return String.join("\n",
                h2("Parse outcome", "Parse outcome") + "<p class=\"note\">Distinct files.</p>"
                        + StackedChart.render("Parse outcome", parseOutcome(summary)),
                h2("Package managers", "Package managers")
                        + "<p class=\"note\">A location using two managers counts for each.</p>"
                        + StackedChart.render("Package managers", managers(summary.managers())),
                folded(counts(summary.managers(), "Manager")),
                h2("Final user", "Final user"), "<p class=\"note\">" + userNote(summary) + "</p>",
                StackedChart.render("Final user", users(summary.users())),
                folded(counts(summary.users(), "User")));
    }

    static List<Segment> managers(Map<String, Integer> managers) {
        return managers.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Integer> e) -> managerRank(e.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(e -> new Segment(e.getKey(), e.getValue()))
                .toList();
    }

    static int managerRank(String manager) {
        int i = MANAGER_ORDER.indexOf(manager);
        return i < 0 ? MANAGER_ORDER.size() : i;
    }

    static List<Segment> users(Map<String, Integer> users) {
        return USER_ORDER.stream()
                .map(u -> new Segment(u.toLowerCase(Locale.ROOT), users.getOrDefault(u, 0)))
                .toList();
    }

    static List<Segment> parseOutcome(Summary s) {
        int files = s.passes().isEmpty() ? 0 : s.passes().getFirst().groups();
        int failed = (int) Math.round(s.parseFailureRate() * (files - s.templates()));
        return List.of(new Segment("parsed", files - s.templates() - failed),
                new Segment("templates", s.templates()), new Segment("failed", failed));
    }

    static String counts(Map<String, Integer> counts, String heading) {
        String rows = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> row(escape(e.getKey()), count(e.getValue())))
                .collect(Collectors.joining());
        return "<table><tr><th>" + heading + "</th><th class=\"num\">Locations</th></tr>" + rows + "</table>";
    }

    static String userNote(Summary s) {
        return s.effectiveUsers()
                ? "Users are effective, from the built images where a Dockerfile links to one; as written otherwise."
                : "Users are as written in the Dockerfile: lineage has not run, so inherited users are not known.";
    }
}
