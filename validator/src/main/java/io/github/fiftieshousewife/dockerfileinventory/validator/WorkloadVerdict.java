package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

record WorkloadVerdict(String blobSha, String kind, String name, String namespace, List<Location> locations,
                       Verdict verdict, List<Finding> findings) {

    WorkloadVerdict {
        locations = List.copyOf(locations);
        findings = List.copyOf(findings);
    }

    static WorkloadVerdict of(Workload workload, Profile profile) {
        List<Finding> findings = PodValidation.findings(workload, profile);
        return new WorkloadVerdict(workload.blobSha(), workload.kind(), workload.name(), workload.namespace(),
                workload.locations(), Verdict.of(findings), findings);
    }

    static Map<String, Object> summary(List<WorkloadVerdict> verdicts) {
        Map<Verdict, Long> counted = verdicts.stream().collect(Collectors.groupingBy(WorkloadVerdict::verdict,
                () -> new EnumMap<>(Verdict.class), Collectors.counting()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("workloads", verdicts.size());
        summary.put("workloadVerdicts", counted.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().name().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a,
                LinkedHashMap::new)));
        return summary;
    }
}
