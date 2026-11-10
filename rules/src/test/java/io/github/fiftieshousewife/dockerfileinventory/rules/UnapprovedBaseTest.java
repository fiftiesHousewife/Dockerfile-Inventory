package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UnapprovedBaseTest {

    private static final Rule S1 = new UnapprovedBase(BaseList.read(Path.of("src/test/resources/fixtures/allow.csv")));

    @Test
    void s1ReportsABaseNotOnTheApprovedListAndAnUnresolvedOne() {
        assertAll(
                () -> assertThat(S1.check(Subjects.of("FROM registry.example.com/ubi9/ubi-minimal:9.4\n"))).isEmpty(),
                () -> assertThat(S1.check(Subjects.of("FROM ubi9/ubi:latest\n"))).isEmpty(),
                () -> assertThat(S1.check(Subjects.of("FROM ubi9/ubi-minimal:8.10\n"))).singleElement()
                        .extracting(Finding::message).asString().contains("ubi9/ubi-minimal:8.10"),
                () -> assertThat(S1.check(Subjects.of("FROM alpine:3.20\n"))).hasSize(1),
                () -> assertThat(S1.check(Subjects.of("ARG BASE\nFROM $BASE\n"))).singleElement()
                        .extracting(Finding::message).asString().contains("build argument"));
    }

    @Test
    void s1IgnoresAFileThatDidNotParse() {
        assertThat(S1.check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }
}
