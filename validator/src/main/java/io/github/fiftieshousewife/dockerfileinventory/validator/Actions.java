package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Findings turned into actions, most urgent first, and the same actions per repository.
final class Actions {

    static final Map<Check, String> WHAT = Map.of(
            Check.P1, "set a numeric, non-root USER (USER 1001) and make the paths it writes group-0 writable",
            Check.P2, "make the paths written at run time group-0 writable (chgrp -R 0 and chmod -R g=u) before USER",
            Check.P3, "listen on a port above 1024 and let the Service expose the low one",
            Check.P4, "drop sudo, su and gosu: start the process as the image's own user",
            Check.P5, "remove the setuid bit and give the program what it needs without escalation",
            Check.P6, "write the ENTRYPOINT in exec form, or start through an init that forwards signals (tini)",
            Check.P7, "build from a base in an allowed registry, or mirror the base there",
            Check.P8, "pin the base by digest (FROM name:tag@sha256:...) and let a bot update it",
            Check.P9, "declare the probes and volumes in the deployment",
            Check.P10, "stop relying on the user's name or home at start, or give the UID a passwd entry "
                    + "(nss_wrapper)");

    private static final Comparator<Action> URGENCY = Comparator.comparing(Action::severity)
            .thenComparing(Comparator.comparingInt(Action::reach).reversed())
            .thenComparing(Action::certainty)
            .thenComparing(Action::check)
            .thenComparing(Action::blobSha);

    private Actions() {
    }

    static List<Action> of(List<Finding> findings, Map<String, List<Location>> locations) {
        Map<String, List<Finding>> byFix = findings.stream().collect(Collectors.groupingBy(
                f -> f.blobSha() + " " + f.check(), LinkedHashMap::new, Collectors.toList()));
        return byFix.values().stream().map(group -> action(group, locations)).sorted(URGENCY).toList();
    }

    static Map<String, List<Action>> byRepository(List<Action> actions) {
        Map<String, List<Action>> byRepository = new TreeMap<>();
        actions.forEach(action -> action.where().stream().map(Location::repository).distinct()
                .forEach(repository -> byRepository.computeIfAbsent(repository, r -> new ArrayList<>())
                        .add(action)));
        return byRepository;
    }

    private static Action action(List<Finding> group, Map<String, List<Location>> locations) {
        Finding first = group.getFirst();
        Certainty certainty = group.stream().map(Finding::certainty).min(Comparator.naturalOrder()).orElseThrow();
        return new Action(first.check(), first.severity(), certainty, WHAT.get(first.check()), first.blobSha(),
                group.stream().map(Finding::message).toList(), locations.getOrDefault(first.blobSha(), List.of()));
    }
}
