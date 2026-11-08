package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

/**
 * One stage of a Dockerfile: its name (or null), its base or the earlier stage it builds on, what it copies from;
 * its instructions, {@code RUN}s, the shell commands in them, the {@code RUN}s the shell reader could not read and
 * the layers it adds; every {@code COPY} and {@code ADD}; every change of ownership or mode in its {@code RUN}s;
 * its unusual steps (neither the everyday instructions nor their everyday forms), in order.
 */
public record ImageStage(String name, BaseImage base, String fromStage, List<String> copiesFrom, int instructions,
                         int runs, int commands, int unreadRuns, int layers, List<CopyFact> copies,
                         List<PermissionChange> permissions, List<String> unusual) {

    public ImageStage {
        copiesFrom = List.copyOf(copiesFrom);
        copies = List.copyOf(copies);
        permissions = List.copyOf(permissions);
        unusual = List.copyOf(unusual);
    }
}
