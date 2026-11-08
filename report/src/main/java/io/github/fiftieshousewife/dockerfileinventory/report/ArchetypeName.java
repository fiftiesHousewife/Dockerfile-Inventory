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
        String[] f = signature.split("\\|", -1);
        String stages = stages(f[6]);
        return base(f[0]) + (f[2].isEmpty() ? ", no packages" : " with " + and(f[1]) + " (" + packages(f[2]) + ")")
                + ", " + user(f[3]) + (stages.equals("1") ? "" : ", " + stages + " stages");
    }

    static Map<String, String> attributes(String signature) {
        String[] f = signature.split("\\|", -1);
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("Base family", base(f[0]));
        attributes.put("Package managers", orNone(f[1].replace(",", ", ")));
        attributes.put("Packages", orNone(f[2].replace(",", ", ")));
        attributes.put("User", user(f[3]));
        attributes.put("Ports", orNone(f[4].replace(",", ", ")));
        attributes.put("Start command", switch (f[5]) {
            case "EXEC" -> "exec form";
            case "SHELL" -> "shell form";
            default -> "none of its own";
        });
        attributes.put("Stages", stages(f[6]));
        return attributes;
    }

    static String base(String familyAndMajor) {
        int colon = familyAndMajor.lastIndexOf(':');
        String family = colon < 0 ? familyAndMajor : familyAndMajor.substring(0, colon);
        String major = colon < 0 ? "" : familyAndMajor.substring(colon + 1);
        String name = family.equals(UNRESOLVED_BASE) ? "base set by a variable" : family;
        return major.isEmpty() ? name : name + " " + major;
    }

    static String user(String kind) {
        return switch (kind) {
            case "ROOT" -> "root";
            case "NUMERIC" -> "numeric user";
            case "NAMED" -> "named user";
            default -> "user from a variable";
        };
    }

    private static String packages(String packages) {
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

    private static String orNone(String value) {
        return value.isEmpty() ? "none" : value;
    }
}
