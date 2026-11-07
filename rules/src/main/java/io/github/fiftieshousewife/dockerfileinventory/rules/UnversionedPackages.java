package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.StagedCall;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class UnversionedPackages implements Rule {

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
                .flatMap(staged -> finding(subject, staged).stream())
                .toList();
    }

    Optional<Finding> finding(Subject subject, StagedCall staged) {
        List<String> loose = staged.call().packages().stream().filter(p -> !versioned(p)).toList();
        return loose.isEmpty() ? Optional.empty() : Optional.of(finding(subject, null,
                "%s %s without versions: %s".formatted(staged.call().manager(), staged.call().subcommand(),
                        String.join(", ", loose))));
    }

    static boolean versioned(String pkg) {
        return pkg.contains("=") || VERSION.matcher(pkg).find();
    }
}
