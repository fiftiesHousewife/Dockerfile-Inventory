package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

record WorkloadVerdict(String blobSha, String kind, String name, String namespace, List<Location> locations,
                       Verdict verdict, List<ContainerLink> containers, List<Finding> findings) {

    WorkloadVerdict {
        locations = List.copyOf(locations);
        containers = List.copyOf(containers);
        findings = List.copyOf(findings);
    }

    static WorkloadVerdict of(Workload workload, Profile profile) {
        return of(workload, profile, ImageLinks.NONE, Map.of());
    }

    static WorkloadVerdict of(Workload workload, Profile profile, ImageLinks links, Map<String, Subject> subjects) {
        List<ContainerLink> containers = workload.pod().containers().stream().map(links::link).toList();
        List<Finding> findings = Stream.concat(PodValidation.findings(workload, profile).stream(),
                workload.pod().containers().stream().flatMap(container -> imageFindings(links.link(container),
                        container, subjects, profile))).toList();
        return new WorkloadVerdict(workload.blobSha(), workload.kind(), workload.name(), workload.namespace(),
                workload.locations(), Verdict.of(findings), containers, findings);
    }

    static Stream<Finding> imageFindings(ContainerLink link, Workload.Container container,
                                         Map<String, Subject> subjects, Profile profile) {
        return link.dockerfiles().stream().filter(subjects::containsKey)
                .flatMap(sha -> ContainerImage.findings(subjects.get(sha), container, profile).stream());
    }

    List<Finding> podFindings() {
        return findings.stream().filter(finding -> finding.check().pod()).toList();
    }

    static Map<String, Object> summary(List<WorkloadVerdict> verdicts) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("workloads", verdicts.size());
        summary.put("workloadVerdicts", ActionPlan.counts(verdicts.stream().map(WorkloadVerdict::verdict)));
        return summary;
    }
}
