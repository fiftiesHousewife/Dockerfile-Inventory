package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EndOfLifeBaseTest {

    private static final Rule R2 = new EndOfLifeBase(BaseList.read(Path.of("src/test/resources/fixtures/eol.csv")));

    @Test
    void r2ReportsABaseTheEndOfLifeListMarksEol() {
        assertThat(R2.check(Subjects.of("FROM ubi9/ubi-minimal:9.2-1100\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("2025-11-30"));
        assertThat(R2.check(Subjects.of("FROM ubi9/ubi-minimal:9.4\n"))).isEmpty();
        assertThat(R2.check(Subjects.of("FROM alpine:3.20\n"))).isEmpty();
    }

    @Test
    void r2IgnoresAnUnresolvedBaseAndAFileThatDidNotParse() {
        assertThat(R2.check(Subjects.of("ARG BASE\nFROM $BASE\n"))).isEmpty();
        assertThat(R2.check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }
}
