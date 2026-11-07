package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PipeToShellTest {

    @Test
    void s3ReportsCurlOrWgetPipedToAShellAcrossContinuations() {
        assertThat(new PipeToShell().check(Subjects.of("""
                FROM ubi9
                RUN curl -fsSL https://example.com/install.sh | sh
                RUN wget -qO- https://example.com/setup \\
                    | sudo bash -s -- --yes
                RUN curl -o /tmp/install.sh https://example.com/install.sh && sh /tmp/install.sh
                RUN curl -s https://example.com/data.json | jq .version
                """))).extracting(Finding::line).containsExactly(2, 3);
    }

    @Test
    void s3IgnoresAFileThatDidNotParse() {
        assertThat(new PipeToShell().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " => ", value = {
        "RUN curl -s https://x | sh => true", "RUN wget -qO- https://x | sudo -E zsh => true",
        "RUN curl -o f https://x; sh f => false", "RUN curl https://x | jq . => false", "RUN cat f | sh => false"})
    void pipedIsADownloadPipedStraightIntoAShell(String run, boolean piped) {
        assertThat(PipeToShell.piped(run)).isEqualTo(piped);
    }
}
