package io.github.fiftieshousewife.dockerfileinventory.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArgumentsTest {

    @Test
    void theArgumentsAreTheOrganisationsUrlAndOptionallyATry() {
        assertAll(
                () -> assertThat(Arguments.of("https://github.com/acme"))
                        .isEqualTo(new Arguments(List.of("https://github.com/acme"), false)),
                () -> assertThat(Arguments.of("--try", "https://github.com/acme"))
                        .isEqualTo(new Arguments(List.of("https://github.com/acme"), true)),
                () -> assertThat(Arguments.of("https://github.com/acme", "https://github.com/beta"))
                        .isEqualTo(new Arguments(List.of("https://github.com/acme", "https://github.com/beta"),
                                false)));
    }

    @Test
    void anythingElseIsRefusedWithTheUsage() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(Arguments::of).withMessage(Arguments.USAGE),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Arguments.of("--all", "x"))
                        .withMessage(Arguments.USAGE),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Arguments.of("--try"))
                        .withMessage(Arguments.USAGE));
    }
}
