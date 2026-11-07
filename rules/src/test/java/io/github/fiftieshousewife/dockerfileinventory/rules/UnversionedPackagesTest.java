package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnversionedPackagesTest {

    @Test
    void i1ReportsInstalledPackagesWithoutAVersion() {
        Rule i1 = new UnversionedPackages();

        assertThat(i1.check(Subjects.of("FROM ubi9\nRUN dnf install -y git-2.43.5 tar curl\n"))).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("tar, curl").doesNotContain("git"));
        assertThat(i1.check(Subjects.of("FROM debian\nRUN apt-get install -y git=1:2.39 curl=7.88\n"))).isEmpty();
        assertThat(i1.check(Subjects.of("FROM ubi9\nRUN dnf clean all\n"))).isEmpty();
    }

    @Test
    void i1IgnoresAFileThatDidNotParse() {
        assertThat(new UnversionedPackages().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void aCallWithEveryPackageVersionedHasNoFinding() {
        Subject subject = Subjects.of("FROM debian\nRUN apt-get install -y git=1:2.39\n");

        assertThat(new UnversionedPackages().finding(subject, subject.facts().packageCalls().getFirst())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"git=1:2.39, true", "git-2.43.5, true", "curl, false", "java-17-openjdk, true", "tar, false"})
    void versionedReadsEqualsOrADigitAfterAHyphenSoJavaSeventeenOpenjdkCountsAsVersioned(String pkg,
                                                                                         boolean versioned) {
        assertThat(UnversionedPackages.versioned(pkg)).isEqualTo(versioned);
    }
}
