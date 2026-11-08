package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PackageInstallRenderer {

    private static final Map<String, PackageManager> BY_FAMILY = Map.of(
            "ubi-minimal", PackageManager.MICRODNF,
            "ubi", PackageManager.DNF,
            "alpine", PackageManager.APK,
            "debian", PackageManager.APT,
            "ubuntu", PackageManager.APT);

    private PackageInstallRenderer() {
    }

    static List<String> commands(PackageInstall install) {
        String packages = String.join(" ", install.packages());
        return switch (install.manager()) {
            case MICRODNF, DNF, YUM -> {
                String tool = install.manager().name().toLowerCase(Locale.ROOT);
                yield List.of(tool + " install " + flags(install, "-y --nodocs --setopt=install_weak_deps=0")
                        + packages, tool + " clean all");
            }
            case APK -> List.of("apk add " + flags(install, "--no-cache") + packages);
            case APT -> List.of("apt-get update", "apt-get install " + flags(install, "-y --no-install-recommends")
                    + packages, "rm -rf /var/lib/apt/lists/*");
        };
    }

    static String flags(PackageInstall install, String house) {
        return (install.flags().isEmpty() ? house : String.join(" ", install.flags())) + " ";
    }

    static PackageManager forBase(BaseImage base) {
        PackageManager manager = BY_FAMILY.get(base.family());
        if (manager == null) {
            throw new IllegalArgumentException("no known package manager for " + base.name() + "; name one");
        }
        return manager;
    }
}
