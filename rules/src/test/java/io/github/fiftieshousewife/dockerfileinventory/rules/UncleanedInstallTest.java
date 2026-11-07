package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UncleanedInstallTest {

    @Test
    void h1ReportsAnRpmInstallNotCleanedInTheSameRun() {
        Rule h1 = new UncleanedInstall();

        assertThat(h1.check(Subjects.of("FROM ubi9\nRUN microdnf install -y git\nRUN microdnf clean all\n")))
                .singleElement().satisfies(f -> assertThat(f.message()).contains("microdnf install"));
        assertThat(h1.check(Subjects.of("FROM ubi9\nRUN dnf install -y git && dnf clean all\n"))).isEmpty();
        assertThat(h1.check(Subjects.of("FROM alpine\nRUN apk add --no-cache git\n"))).isEmpty();
        assertThat(h1.check(Subjects.of("FROM debian\nRUN apt-get install -y git\n"))).isEmpty();
    }

    @Test
    void h1IgnoresAFileThatDidNotParse() {
        assertThat(new UncleanedInstall().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }
}
