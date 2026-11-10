package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Map;

final class CheckNames {

    static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("P1", "Runs as root, or as a user that may be root"),
            Map.entry("P2", "Paths written at run time not writable by group 0"),
            Map.entry("P3", "Listens on a port below 1024"),
            Map.entry("P4", "sudo, su or gosu to start"),
            Map.entry("P5", "A setuid or setgid bit"),
            Map.entry("P6", "Shell-form start: the process never receives SIGTERM"),
            Map.entry("P7", "Base from a registry the cluster does not allow"),
            Map.entry("P8", "Base by tag, not pinned by digest"),
            Map.entry("P9", "HEALTHCHECK or VOLUME the deployment must supply"),
            Map.entry("P10", "Start reads the user's name or home"),
            Map.entry("P11", "Floating base: latest or no tag"),
            Map.entry("D1", "Privileged container"),
            Map.entry("D2", "Fixed runAsUser"),
            Map.entry("D3", "Added capability the SCC refuses"),
            Map.entry("D4", "Privilege escalation allowed"),
            Map.entry("D5", "Host network, PID, IPC, path or port"),
            Map.entry("D6", "Unconfined seccomp profile"),
            Map.entry("D7", "Container image floating: latest or no tag"),
            Map.entry("S1", "Base not on the approved list"),
            Map.entry("S2", "Secret in an ENV or ARG"),
            Map.entry("S3", "Download piped to a shell"),
            Map.entry("S4", "Cannot run as an arbitrary non-root user"),
            Map.entry("S5", "World-writable chmod (777, o+w)"),
            Map.entry("S6", "ADD of a URL without a checksum"),
            Map.entry("S7", "Key or credential copied in"),
            Map.entry("R1", "Base not pinned by digest"),
            Map.entry("R2", "Base past its end of life"),
            Map.entry("R3", "No source or revision label"),
            Map.entry("R4", "One base pinned at several digests"),
            Map.entry("H1", "Package cache left in the layer"),
            Map.entry("H2", "Shell-form entrypoint"),
            Map.entry("H3", "Adjacent RUNs that could be one"),
            Map.entry("H4", "Every package upgraded in the build"),
            Map.entry("H5", "ADD where COPY would do"),
            Map.entry("H6", "Owner or mode changed after the copy, stored twice"),
            Map.entry("H7", "Path changed after its VOLUME"),
            Map.entry("H8", "Copied file removed in a later layer"),
            Map.entry("H9", "DEBIAN_FRONTEND left set in the image"),
            Map.entry("I1", "Packages without versions"),
            Map.entry("I2", "Does not parse"));

    private CheckNames() {
    }

    static String of(String check) {
        return NAMES.getOrDefault(check, check);
    }
}
