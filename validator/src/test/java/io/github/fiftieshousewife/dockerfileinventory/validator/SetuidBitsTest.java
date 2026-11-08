package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SetuidBitsTest {

    private final SetuidBits check = new SetuidBits();

    @Test
    void aSetuidBitInTheFinalImageIsAProblem() {
        assertThat(check.problems(subject("026d8695"), V2)).containsExactly(
                "chmod +s /usr/bin/nnp-test sets a setuid bit, relying on privilege escalation, which "
                        + "restricted-v2 forbids");
    }

    @Test
    void ordinaryModesAreNot() {
        assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty();
    }

    @Test
    void setuidIsAFourOrSixThousandModeOrASymbolicSForTheUser() {
        assertAll(
                () -> assertThat(SetuidBits.setuid("4755")).isTrue(),
                () -> assertThat(SetuidBits.setuid("6755")).isTrue(),
                () -> assertThat(SetuidBits.setuid("u+s")).isTrue(),
                () -> assertThat(SetuidBits.setuid("+xs")).isTrue(),
                () -> assertThat(SetuidBits.setuid("2775")).isFalse(),
                () -> assertThat(SetuidBits.setuid("g+s")).isFalse(),
                () -> assertThat(SetuidBits.setuid("444")).isFalse());
    }
}
