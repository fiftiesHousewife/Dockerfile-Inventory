package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

/** I2: a Dockerfile the parser rejects, which no other rule can read. */
public final class Unparseable implements Rule {

    @Override
    public String id() {
        return "I2";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Finding> check(Subject subject) {
        return subject.isParsed() ? List.of() : List.of(finding(subject, null, subject.failure()));
    }
}
