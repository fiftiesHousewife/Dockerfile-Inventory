package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class WorkloadTable {

    static final List<String> WORST_FIRST = List.of("REFUSED", "BREAKS", "DRIFT", "INFORMATION", "RUNS");

    private static final Comparator<ActionPlanView.PlannedWorkload> ORDER = Comparator
            .<ActionPlanView.PlannedWorkload>comparingInt(w -> WORST_FIRST.indexOf(w.verdict()))
            .thenComparing(ActionPlanView.PlannedWorkload::name, Comparator.nullsLast(Comparator.naturalOrder()));

    private WorkloadTable() {
    }

    static String render(List<ActionPlanView.PlannedWorkload> workloads, Set<String> catalogued) {
        if (workloads.isEmpty()) {
            return "";
        }
        int hidden = workloads.size() - ActionPlanPage.SHOWN;
        return "<h2>Workloads, worst first</h2><div class=\"scroll\"><table><tr><th>Verdict</th><th>Workload</th>"
                + "<th>File</th><th>Containers</th><th>Findings</th></tr>"
                + workloads.stream().sorted(ORDER).limit(ActionPlanPage.SHOWN).map(w -> row(w, catalogued))
                .collect(Collectors.joining())
                + "</table></div>" + (hidden > 0 ? "<p>and " + count(hidden)
                + " more in the validator's workload-verdicts.jsonl.</p>" : "");
    }

    static String row(ActionPlanView.PlannedWorkload workload, Set<String> catalogued) {
        String file = workload.locations().stream().findFirst().map(w -> w.repository() + " " + w.path()).orElse("");
        return "<tr><td>" + escape(workload.verdict().toLowerCase(Locale.ROOT)) + "</td><td>"
                + escape(workload.kind() + " " + workload.name()
                + (workload.namespace() == null ? "" : " (" + workload.namespace() + ")"))
                + "</td><td>" + escape(file) + "</td><td>" + containers(workload, catalogued) + "</td><td>"
                + escape(workload.findings().stream().map(f -> f.check() + ": " + f.message())
                .collect(Collectors.joining("; "))) + "</td></tr>";
    }

    static String containers(ActionPlanView.PlannedWorkload workload, Set<String> catalogued) {
        return workload.containers().stream().map(c -> escape(c.name() + ": " + image(c)) + (c.unlinked() != null
                        ? escape(", unlinked: " + c.unlinked())
                        : ", built from " + c.dockerfiles().stream().map(sha -> dockerfile(sha, catalogued))
                        .collect(Collectors.joining(", "))))
                .collect(Collectors.joining("; "));
    }

    static String image(ActionPlanView.PlannedContainer container) {
        String image = container.image().isBlank() ? "no image" : container.image().strip();
        return container.floating() ? image + " (floats)" : image;
    }

    static String dockerfile(String sha, Set<String> catalogued) {
        String shown = escape(sha.substring(0, Math.min(8, sha.length())));
        return catalogued.contains(sha) ? "<a href=\"dockerfiles/" + escape(sha) + ".html\">" + shown + "</a>"
                : shown;
    }
}
