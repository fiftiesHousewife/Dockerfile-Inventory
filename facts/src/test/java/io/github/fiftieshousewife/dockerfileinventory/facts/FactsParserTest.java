package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FactsParserTest {

    @Test
    void parsesAMinimalDockerfileToOneStage() {
        ParseResult result = new FactsParser().parse("abc", "FROM ubi9/ubi-minimal\n");

        assertThat(result).isInstanceOfSatisfying(ParseResult.Parsed.class,
                parsed -> assertThat(parsed.file().getStages()).hasSize(1));
    }

    @Test
    void recordsTextTheGrammarRejectsAsAFailure() {
        ParseResult result = new FactsParser().parse("bad", "this is not a dockerfile\n");

        assertThat(result).isInstanceOfSatisfying(ParseResult.Failed.class, failed -> {
            assertThat(failed.blobSha()).isEqualTo("bad");
            assertThat(failed.message()).isNotBlank();
        });
    }

    @Test
    void recordsALineTheParserGluedOntoThePreviousInstructionAsAFailure() {
        ParseResult result = new FactsParser().parse("typo", "FROM ubi9\nRUNN echo typo\n");

        assertThat(result).isInstanceOfSatisfying(ParseResult.Failed.class,
                failed -> assertThat(failed.message()).contains("RUNN echo typo"));
    }

    @Test
    void acceptsShellContinuationsInsideARun() {
        assertThat(new FactsParser().parse("ok", "FROM ubi9\nRUN echo a \\\n  && echo b\n"))
                .isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void acceptsAHeredocRun() {
        assertThat(new FactsParser().parse("ok", "FROM ubi9\nRUN <<EOF\nmicrodnf install -y git\nmicrodnf clean all\nEOF\n"))
                .isInstanceOf(ParseResult.Parsed.class);
    }
}
