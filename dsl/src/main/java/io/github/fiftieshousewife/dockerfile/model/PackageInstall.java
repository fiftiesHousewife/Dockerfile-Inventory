package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/**
 * Packages to install with one manager; the renderer always cleans in the same {@code RUN} (H1). With no flags the
 * house flags apply (no weak dependencies or documentation, to keep the layer small); with flags, exactly those
 * render, so a converted Dockerfile keeps its own.
 */
public record PackageInstall(PackageManager manager, List<String> flags, List<String> packages) {

    public PackageInstall {
        flags = List.copyOf(flags);
        packages = List.copyOf(packages);
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("an install needs at least one package");
        }
        packages.forEach(p -> Checks.notBlank(p, "a package name"));
    }

    /** An install with the house flags. */
    public PackageInstall(PackageManager manager, List<String> packages) {
        this(manager, List.of(), packages);
    }
}
