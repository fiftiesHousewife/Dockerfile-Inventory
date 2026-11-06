package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;

/** Runs every rule over every Dockerfile. */
public final class RuleRun {

    private final List<Rule> rules;

    public RuleRun(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Each Dockerfile through every rule in turn; a rule that fails on one file is reported there, not fatal. */
    public List<Finding> run(List<Subject> subjects) {
        List<Finding> findings = new ArrayList<>();
        for (Subject subject : subjects) {
            for (Rule rule : rules) {
                try {
                    findings.addAll(rule.check(subject));
                } catch (RuntimeException e) {
                    findings.add(rule.finding(subject, null, "rule failed: " + e.getMessage()));
                }
            }
        }
        return findings;
    }
}
