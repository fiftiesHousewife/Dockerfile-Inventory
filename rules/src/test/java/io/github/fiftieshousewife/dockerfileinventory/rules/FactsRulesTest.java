package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FactsRulesTest {

    @Test
    void r1ReportsABaseWithNoDigestButNotAnUnresolvedOne() {
        Rule r1 = new UnpinnedBase();

        assertThat(r1.check(Subjects.of("FROM ubi9/ubi-minimal:9.4\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("ubi9/ubi-minimal:9.4"));
        assertThat(r1.check(Subjects.of("FROM ubi9/ubi-minimal:9.4@sha256:" + "a".repeat(64) + "\n"))).isEmpty();
        assertThat(r1.check(Subjects.of("ARG BASE\nFROM $BASE\n"))).isEmpty();
    }

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
    void h2ReportsAShellFormFinalEntrypoint() {
        Rule h2 = new ShellEntrypoint();

        assertThat(h2.check(Subjects.of("FROM ubi9\nENTRYPOINT java -jar app.jar\n"))).hasSize(1);
        assertThat(h2.check(Subjects.of("FROM ubi9\nENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n"))).isEmpty();
        assertThat(h2.check(Subjects.of("FROM ubi9\n"))).isEmpty();
    }

    @Test
    void i1ReportsInstalledPackagesWithoutAVersion() {
        Rule i1 = new UnversionedPackages();

        assertThat(i1.check(Subjects.of("FROM ubi9\nRUN dnf install -y git-2.43.5 tar curl\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("tar, curl").doesNotContain("git"));
        assertThat(i1.check(Subjects.of("FROM debian\nRUN apt-get install -y git=1:2.39 curl=7.88\n"))).isEmpty();
        assertThat(i1.check(Subjects.of("FROM ubi9\nRUN dnf clean all\n"))).isEmpty();
    }

    @Test
    void i2ReportsEachParseFailureWithItsMessage() {
        Rule i2 = new Unparseable();

        assertThat(i2.check(Subjects.of("not a dockerfile\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).startsWith("Syntax error"));
        assertThat(i2.check(Subjects.of("FROM ubi9\n"))).isEmpty();
    }
}
