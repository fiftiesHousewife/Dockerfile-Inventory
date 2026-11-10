package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class UnparseableTest {

    @Test
    void i2ReportsEachParseFailureWithItsMessage() {
        Rule i2 = new Unparseable();

        assertAll(
                () -> assertThat(i2.check(Subjects.of("not a dockerfile\n"))).singleElement()
                        .extracting(Finding::message).asString().startsWith("Syntax error"),
                () -> assertThat(i2.check(Subjects.of("FROM ubi9\n"))).isEmpty());
    }
}
