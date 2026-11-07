package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BasesTest {

    @Test
    void theReferenceIsTheNameWithItsTagAndNeverTheDigest() {
        assertThat(Bases.reference(Subjects.of("FROM ubi9:9.4@sha256:" + "a".repeat(64) + "\n").facts().base()))
                .isEqualTo("ubi9:9.4");
        assertThat(Bases.reference(Subjects.of("FROM ubi9\n").facts().base())).isEqualTo("ubi9");
    }
}
