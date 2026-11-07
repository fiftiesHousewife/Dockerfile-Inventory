package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorpusOutcomeTest {

    @Test
    void aRoundTripIsNamedPassesFailsOrNotParsed() {
        assertThat(CorpusOutcome.roundTrip("FROM ubi9\nRUN make\n")).isEqualTo("passes");
        assertThat(CorpusOutcome.roundTrip("not a dockerfile\n")).isEqualTo("not-parsed");
        assertThat(CorpusOutcome.roundTrip("FROM ubi9@sha256:short\n")).isEqualTo("fails");
    }
}
