package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.regex.Pattern;

// Whether an installed package names its version, by each manager's syntax.
final class Versions {

    private static final Pattern RPM = Pattern.compile(".+-\\d[\\w.+~^]*(-[\\w.+~^]+)?");

    private Versions() {
    }

    static boolean versioned(String manager, String name) {
        return switch (manager) {
            case "apt", "apt-get", "apk" -> name.contains("=");
            case "dnf", "yum", "microdnf", "rpm" -> RPM.matcher(name).matches();
            case "pip", "pip3" -> name.contains("==");
            default -> false;
        };
    }
}
