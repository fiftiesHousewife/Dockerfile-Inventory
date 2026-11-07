package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

final class RuleRun {

    private final List<Rule> rules;

    RuleRun(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    List<Finding> run(List<Subject> subjects) {
        return subjects.stream()
                .flatMap(subject -> rules.stream().flatMap(rule -> check(rule, subject).stream()))
                .toList();
    }

    static List<Finding> check(Rule rule, Subject subject) {
        try {
            return rule.check(subject);
        } catch (RuntimeException e) {
            return List.of(rule.finding(subject, null, "rule failed: " + e.getMessage()));
        }
    }
}
