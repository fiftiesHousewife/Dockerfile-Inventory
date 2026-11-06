package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ListRulesTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    @Test
    void s1ReportsABaseNotOnTheApprovedListAndAnUnresolvedOne() {
        Rule s1 = new UnapprovedBase(BaseList.read(FIXTURES.resolve("allow.csv")));

        assertThat(s1.check(Subjects.of("FROM registry.example.com/ubi9/ubi-minimal:9.4\n"))).isEmpty();
        assertThat(s1.check(Subjects.of("FROM ubi9/ubi:latest\n"))).isEmpty();
        assertThat(s1.check(Subjects.of("FROM ubi9/ubi-minimal:8.10\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("ubi9/ubi-minimal:8.10"));
        assertThat(s1.check(Subjects.of("FROM alpine:3.20\n"))).hasSize(1);
        assertThat(s1.check(Subjects.of("ARG BASE\nFROM $BASE\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("build argument"));
    }

    @Test
    void r2ReportsABaseTheEndOfLifeListMarksEol() {
        Rule r2 = new EndOfLifeBase(BaseList.read(FIXTURES.resolve("eol.csv")));

        assertThat(r2.check(Subjects.of("FROM ubi9/ubi-minimal:9.2-1100\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("2025-11-30"));
        assertThat(r2.check(Subjects.of("FROM ubi9/ubi-minimal:9.4\n"))).isEmpty();
        assertThat(r2.check(Subjects.of("FROM alpine:3.20\n"))).isEmpty();
    }
}
