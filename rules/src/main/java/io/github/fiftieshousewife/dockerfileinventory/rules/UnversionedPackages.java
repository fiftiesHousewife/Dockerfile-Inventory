package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** I1: packages installed without a version; reported only, as a pinned base digest is the policy. */
public final class UnversionedPackages implements Rule {

    private static final Pattern VERSION = Pattern.compile("-\\d");

    @Override
    public String id() {
        return "I1";
    }

    @Override
    public Severity severity() {
        return Severity.INFO;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        return subject.facts().packageCalls().stream()
                .filter(staged -> !staged.call().packages().isEmpty())
                .flatMap(staged -> {
                    List<String> loose = staged.call().packages().stream().filter(p -> !versioned(p)).toList();
                    return loose.isEmpty() ? Stream.<Finding>empty() : Stream.of(finding(subject, null,
                            "%s %s without versions: %s".formatted(staged.call().manager(),
                                    staged.call().subcommand(), String.join(", ", loose))));
                })
                .toList();
    }

    /**
     * {@code name=1.2} (apt, apk) or {@code name-1.2} (rpm). A heuristic: an rpm name with a digit after a hyphen,
     * such as {@code java-17-openjdk}, reads as versioned, so I1 can miss it; it is report-only.
     */
    static boolean versioned(String pkg) {
        return pkg.contains("=") || VERSION.matcher(pkg).find();
    }
}
