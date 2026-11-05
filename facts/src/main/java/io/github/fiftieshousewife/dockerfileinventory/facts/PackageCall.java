package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

/**
 * One package-manager command in a {@code RUN}. {@code cleanedInSameRun} says whether the manager's cache is
 * removed later in the same {@code RUN}, which is what keeps it out of the image layer.
 */
public record PackageCall(String manager, String subcommand, List<String> flags, List<String> packages,
                          boolean cleanedInSameRun) {

    public PackageCall {
        flags = List.copyOf(flags);
        packages = List.copyOf(packages);
    }
}
