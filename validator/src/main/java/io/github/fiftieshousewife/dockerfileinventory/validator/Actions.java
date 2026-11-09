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

    static final Map<Check, String> WHAT = Map.ofEntries(
            Map.entry(Check.P1, "set a numeric, non-root USER (USER 1001) and make the paths it writes group-0 "
                    + "writable"),
            Map.entry(Check.P2, "make the paths written at run time group-0 writable (chgrp -R 0 and chmod -R g=u) "
                    + "before USER"),
            Map.entry(Check.P3, "listen on a port above 1024 and let the Service expose the low one"),
            Map.entry(Check.P4, "drop sudo, su and gosu: start the process as the image's own user"),
            Map.entry(Check.P5, "remove the setuid bit and give the program what it needs without escalation"),
            Map.entry(Check.P6, "write the ENTRYPOINT in exec form, or start through an init that forwards signals "
                    + "(tini)"),
            Map.entry(Check.P7, "build from a base in an allowed registry, or mirror the base there"),
            Map.entry(Check.P8, "pin the base by digest (FROM name:tag@sha256:...) and let a bot update it"),
            Map.entry(Check.P9, "declare the probes and volumes in the deployment"),
            Map.entry(Check.P10, "stop relying on the user's name or home at start, or give the UID a passwd entry "
                    + "(nss_wrapper)"),
            Map.entry(Check.S1, "build from an approved base"),
            Map.entry(Check.S2, "remove the secret from the Dockerfile: pass it at build time as a secret mount, or "
                    + "at run time from the platform"),
            Map.entry(Check.S3, "download, verify a checksum or signature, then run: never pipe a download to a "
                    + "shell"),
            Map.entry(Check.S4, "run as a numeric, non-root user"),
            Map.entry(Check.S5, "give group 0 the write it needs (chmod g=u) instead of making paths writable by "
                    + "everyone"),
            Map.entry(Check.S6, "give the download a checksum (ADD --checksum=sha256:...), or fetch and verify it in a "
                    + "RUN"),
            Map.entry(Check.R1, "pin the base by digest"),
            Map.entry(Check.R2, "move to a base still supported"),
            Map.entry(Check.R3, "label the image with its source and revision (org.opencontainers.image.source, "
                    + ".revision)"),
            Map.entry(Check.H1, "clean the package manager's cache in the same RUN as the install"),
            Map.entry(Check.H2, "write the ENTRYPOINT in exec form"),
            Map.entry(Check.H3, "join adjacent RUNs into one"),
            Map.entry(Check.H4, "build on a newer base instead of upgrading every package in the build, so builds "
                    + "repeat"),
            Map.entry(Check.H5, "use COPY: ADD only earns its place for a tar archive it should unpack"),
            Map.entry(Check.H6, "set the owner and mode as the files are copied (COPY --chown, --chmod) instead of "
                    + "changing them in a later layer, which stores them twice"),
            Map.entry(Check.H7, "change the path before the VOLUME that declares it: after it, whether the change "
                    + "is kept depends on the builder"),
            Map.entry(Check.H8, "use the file where it is copied (RUN --mount=type=bind) or in a build stage, since "
                    + "removing it later leaves it in the image"),
            Map.entry(Check.I1, "pin the packages' versions, or record why not"),
            Map.entry(Check.I2, "fix the Dockerfile so it parses"));

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
