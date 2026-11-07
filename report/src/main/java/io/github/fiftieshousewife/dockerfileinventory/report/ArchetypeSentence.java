package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

final class ArchetypeSentence {

    private static final int PACKAGES_NAMED = 4;

    private ArchetypeSentence() {
    }

    // The signature is facts' NormalForms.signature: base:major|managers|packages|user|ports|entrypoint|stages=N.
    static String of(String signature, String base) {
        if (signature.equals(Passes.TEMPLATES)) {
            return "Templates saved under a Dockerfile's name (Jinja and similar), not Dockerfiles.";
        }
        if (signature.equals(Passes.PARSE_FAILURES)) {
            return "Dockerfiles the parser could not read.";
        }
        String[] f = signature.split("\\|", -1);
        return "Builds on " + base + ", " + installs(f[1], f[2]) + ", " + user(f[3]) + ", " + ports(f[4]) + ", "
                + entrypoint(f[5]) + ", " + stages(f[6]) + ".";
    }

    static String installs(String managers, String packages) {
        List<String> names = packages.isEmpty() ? List.of() : List.of(packages.split(","));
        if (names.isEmpty()) {
            return "installs no packages";
        }
        String shown = String.join(", ", names.subList(0, Math.min(PACKAGES_NAMED, names.size())));
        String more = names.size() > PACKAGES_NAMED ? " and " + (names.size() - PACKAGES_NAMED) + " more" : "";
        return "installs " + names.size() + (names.size() == 1 ? " package" : " packages") + " with "
                + managers.replace(",", " and ") + " (" + shown + more + ")";
    }

    static String user(String kind) {
        return switch (kind) {
            case "ROOT" -> "runs as root";
            case "NUMERIC" -> "runs as a numeric user";
            case "NAMED" -> "runs as a named user";
            default -> "runs as a user set by a variable";
        };
    }

    static String ports(String ports) {
        return ports.isEmpty() ? "exposes no ports" : "exposes " + String.join(" and ", ports.split(","));
    }

    static String entrypoint(String form) {
        return switch (form) {
            case "EXEC" -> "starts with an exec-form command";
            case "SHELL" -> "starts with a shell-form command";
            default -> "sets no start command of its own";
        };
    }

    static String stages(String stages) {
        String n = stages.replace("stages=", "");
        return n.equals("1") ? "in one stage" : "in " + n + " stages";
    }
}
