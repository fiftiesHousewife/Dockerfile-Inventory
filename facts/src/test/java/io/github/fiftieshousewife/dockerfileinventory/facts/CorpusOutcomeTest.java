package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class CorpusOutcomeTest {

    @Test
    void aParseIsNamedParsedRetriedTemplateOrFailed() {
        FactsParser parser = new FactsParser();

        assertAll(
                () -> assertThat(Corpus.outcome(parser.parse("a", "FROM ubi9\n"))).isEqualTo("parsed"),
                () -> assertThat(Corpus.outcome(new ParseResult.Parsed("b", Parsed.file("FROM ubi9\n"), true)))
                        .isEqualTo("retried"),
                () -> assertThat(Corpus.outcome(parser.parse("c", "FROM {{ spec.base }}\n"))).isEqualTo("template"),
                () -> assertThat(Corpus.outcome(parser.parse("d", "not a dockerfile\n"))).isEqualTo("failed"));
    }

    @Test
    void theRatchetMessageNamesTheCaseAndSaysWhatToDo() {
        assertThat(Corpus.ratchet("retried-json-comma", "parse"))
                .contains("retried-json-comma", "parse", "update expected.json");
    }
}
