package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class VersionsTest {

    @Test
    void aPackageIsVersionedByItsManagersSyntax() {
        assertAll(
                () -> assertThat(Versions.versioned("apt-get", "curl=7.88.1-10")).isTrue(),
                () -> assertThat(Versions.versioned("apk", "curl")).isFalse(),
                () -> assertThat(Versions.versioned("microdnf", "tzdata-2024a")).isTrue(),
                () -> assertThat(Versions.versioned("dnf", "python3.11")).isFalse(),
                () -> assertThat(Versions.versioned("dnf", "java-21-openjdk-headless")).isFalse(),
                () -> assertThat(Versions.versioned("pip", "requests==2.31.0")).isTrue(),
                () -> assertThat(Versions.versioned("gem", "rails")).isFalse());
    }
}
