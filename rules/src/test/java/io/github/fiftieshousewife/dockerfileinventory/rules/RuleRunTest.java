package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleRunTest {

    private static final Rule EVERY_FILE = new Rule() {
        @Override
        public String id() {
            return "T1";
        }

        @Override
        public Severity severity() {
            return Severity.LOW;
        }

        @Override
        public List<Finding> check(Subject subject) {
            return List.of(finding(subject, null, "seen"));
        }
    };

    private static final Rule FAILS_ON_B = new Rule() {
        @Override
        public String id() {
            return "T2";
        }

        @Override
        public Severity severity() {
            return Severity.HIGH;
        }

        @Override
        public List<Finding> check(Subject subject) {
            if (subject.blobSha().equals("b")) {
                throw new IllegalStateException("cannot read b");
            }
            return List.of();
        }
    };

    @Test
    void findingsCarryTheirRulesIdAndSeverity() {
        assertThat(new RuleRun(List.of(EVERY_FILE)).run(List.of(Subject.failed("a", "x"))))
                .containsExactly(new Finding("T1", Severity.LOW, "a", null, "seen", null, List.of()));
    }

    @Test
    void aRuleThatThrowsOnOneFileRecordsAnErrorThereAndTheRunContinues() {
        List<Finding> findings = new RuleRun(List.of(FAILS_ON_B, EVERY_FILE))
                .run(List.of(Subject.failed("a", "x"), Subject.failed("b", "x")));

        assertAll(
                () -> assertThat(findings).extracting(Finding::rule, Finding::blobSha)
                        .containsExactly(tuple("T1", "a"), tuple("T2", "b"), tuple("T1", "b")),
                () -> assertThat(findings.get(1).message()).startsWith("rule failed: cannot read b"));
    }

    @Test
    void checkTurnsAThrowingRuleIntoOneFindingNamingTheFailure() {
        assertAll(
                () -> assertThat(RuleRun.check(FAILS_ON_B, Subject.failed("b", "x"))).singleElement().isEqualTo(
                        new Finding("T2", Severity.HIGH, "b", null, "rule failed: cannot read b", null, List.of())),
                () -> assertThat(RuleRun.check(FAILS_ON_B, Subject.failed("a", "x"))).isEmpty());
    }
}
