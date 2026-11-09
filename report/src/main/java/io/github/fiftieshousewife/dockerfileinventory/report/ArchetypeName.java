package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ArchetypeName {

    static final String UNRESOLVED_BASE = "unresolved";
    static final String BUNDLE = "bundle (scratch, metadata only)";
    private static final int PACKAGES_NAMED = 3;

    private ArchetypeName() {
    }

    // The signature is facts' NormalForms.signature: base:major|managers|packages|user|ports|entrypoint|stages=N.
    static String of(String signature) {
        if (signature.equals(Passes.TEMPLATES)) {
            return "template, not a Dockerfile";
        }
        if (signature.equals(Passes.PARSE_FAILURES)) {
            return "could not be parsed";
        }
        String[] fields = signature.split("\\|", -1);
        String stages = stages(fields[6]);
        return base(fields[0]) + (fields[2].isEmpty() ? ", no packages"
                : " with " + and(fields[1]) + " (" + packages(fields[2]) + ")")
                + ", " + user(fields[3]) + (stages.equals("1") ? "" : ", " + stages + " stages");
    }

    static Map<String, String> attributes(String signature) {
        String[] fields = signature.split("\\|", -1);
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("Base family", base(fields[0]));
        attributes.put("Package managers", orNone(fields[1].replace(",", ", ")));
        attributes.put("Packages", orNone(fields[2].replace(",", ", ")));
        attributes.put("User", user(fields[3]));
        attributes.put("Ports", orNone(fields[4].replace(",", ", ")));
        attributes.put("Start command", start(fields[5]));
        attributes.put("Stages", stages(fields[6]));
        return attributes;
    }

    static String base(String familyAndMajor) {
        int colon = familyAndMajor.lastIndexOf(':');
        String family = colon < 0 ? familyAndMajor : familyAndMajor.substring(0, colon);
        String major = colon < 0 ? "" : familyAndMajor.substring(colon + 1);
        String name = family.equals(UNRESOLVED_BASE) ? "base set by a variable" : family;
        return major.isEmpty() ? name : name + " " + major;
    }

    static String start(String form) {
        return switch (form) {
            case "EXEC" -> "exec form";
            case "SHELL" -> "shell form";
            default -> "none of its own";
        };
    }

    static String user(String kind) {
        return switch (kind) {
            case "ROOT" -> "root";
            case "NUMERIC" -> "numeric user";
            case "NAMED" -> "named user";
            default -> "user from a variable";
        };
    }

    static String packages(String packages) {
        List<String> names = List.of(packages.split(","));
        String shown = String.join(", ", names.subList(0, Math.min(PACKAGES_NAMED, names.size())));
        return names.size() > PACKAGES_NAMED ? shown + " and " + (names.size() - PACKAGES_NAMED) + " more" : shown;
    }

    private static String and(String managers) {
        return managers.replace(",", " and ");
    }

    private static String stages(String stages) {
        return stages.replace("stages=", "");
    }

    static String orNone(String value) {
        return value.isEmpty() ? "none" : value;
    }
}
