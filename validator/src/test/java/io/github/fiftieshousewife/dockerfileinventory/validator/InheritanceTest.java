package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class InheritanceTest {

    private final Subject setsNothing = subject("03cab6f0");
    private final Subject setsUserAndStart = subject("080b0412");

    @Test
    void theUserIsInheritedOnlyByADockerfileSettingNone() {
        assertAll(
                () -> assertThat(Inheritance.inherits(Check.P1, setsNothing)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P1, setsUserAndStart)).isFalse());
    }

    @Test
    void theStartCommandIsInheritedOnlyByADockerfileSettingNone() {
        assertAll(
                () -> assertThat(Inheritance.inherits(Check.P4, setsNothing)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P6, setsNothing)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P10, setsNothing)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P6, setsUserAndStart)).isFalse());
    }

    @Test
    void writablePathsAreInheritedUnlessTheDockerfileMakesPathsGroupWritableItself() {
        assertAll(
                () -> assertThat(Inheritance.inherits(Check.P2, setsNothing)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P2, subject("141f718e"))).isFalse());
    }

    @Test
    void portsSetuidFilesHealthchecksAndVolumesAreAlwaysInheritedAndTheBaseNever() {
        assertAll(
                () -> assertThat(Inheritance.inherits(Check.P3, setsUserAndStart)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P5, setsUserAndStart)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P9, setsUserAndStart)).isTrue(),
                () -> assertThat(Inheritance.inherits(Check.P7, setsNothing)).isFalse(),
                () -> assertThat(Inheritance.inherits(Check.P8, setsNothing)).isFalse());
    }

    @Test
    void aRulesFindingIsOnlyEverAboutTheDockerfileItRead() {
        assertThat(Inheritance.inherits(Check.H1, setsNothing)).isFalse();
    }
}
