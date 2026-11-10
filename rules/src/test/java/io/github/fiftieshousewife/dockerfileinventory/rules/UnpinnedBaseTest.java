package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class UnpinnedBaseTest {

    @Test
    void r1ReportsABaseWithNoDigestButNotAnUnresolvedOne() {
        Rule r1 = new UnpinnedBase();

        assertAll(
                () -> assertThat(r1.check(Subjects.of("FROM ubi9/ubi-minimal:9.4\n"))).singleElement()
                        .extracting(Finding::message).asString().contains("ubi9/ubi-minimal:9.4"),
                () -> assertThat(r1.check(Subjects.of("FROM ubi9/ubi-minimal:9.4@sha256:" + "a".repeat(64) + "\n")))
                        .isEmpty(),
                () -> assertThat(r1.check(Subjects.of("ARG BASE\nFROM $BASE\n"))).isEmpty());
    }

    @Test
    void r1LeavesScratchAloneAsItIsNoImageAndHasNoDigest() {
        assertThat(new UnpinnedBase().check(Subjects.of("FROM golang:1.25 AS build\nFROM scratch\n"))).isEmpty();
    }

    @Test
    void r1IgnoresAFileThatDidNotParse() {
        assertThat(new UnpinnedBase().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }
}
