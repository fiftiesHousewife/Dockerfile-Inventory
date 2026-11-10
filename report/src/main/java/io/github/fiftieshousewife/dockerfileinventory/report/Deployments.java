package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class Deployments {

    private Deployments() {
    }

    static Map<String, List<ActionPlanView.PlannedWorkload>> byDockerfile(
            List<ActionPlanView.PlannedWorkload> workloads) {
        return workloads.stream().flatMap(workload -> workload.containers().stream()
                        .flatMap(container -> container.dockerfiles().stream()).distinct()
                        .map(sha -> Map.entry(sha, workload)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    static String section(String blobSha, List<ActionPlanView.PlannedWorkload> workloads) {
        return workloads.isEmpty() ? "" : "<h2>Runs in</h2><p>The workloads whose containers lineage links to this "
                + "Dockerfile, judged on the <a href=\"../openshift.html\">action plan</a>.</p>"
                + Html.scrollTable("<th>Verdict</th><th>Workload</th><th>File</th><th>Containers</th>",
                workloads.stream().map(workload -> WorkloadTable.described(workload) + "<td>"
                        + running(blobSha, workload) + "</td></tr>").collect(Collectors.joining()));
    }

    static String running(String blobSha, ActionPlanView.PlannedWorkload workload) {
        return workload.containers().stream().filter(container -> container.dockerfiles().contains(blobSha))
                .map(container -> escape(container.name() + ": " + WorkloadTable.image(container)))
                .collect(Collectors.joining("; "));
    }
}
