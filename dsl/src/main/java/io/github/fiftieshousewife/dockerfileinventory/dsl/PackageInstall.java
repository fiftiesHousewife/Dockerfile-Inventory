package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

/** Packages to install with one manager; the renderer always cleans in the same {@code RUN} (H1). */
public record PackageInstall(PackageManager manager, List<String> packages) {

    public PackageInstall {
        packages = List.copyOf(packages);
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("an install needs at least one package");
        }
        packages.forEach(p -> Checks.notBlank(p, "a package name"));
    }
}
