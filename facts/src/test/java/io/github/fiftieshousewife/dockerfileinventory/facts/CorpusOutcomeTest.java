package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorpusOutcomeTest {

    @Test
    void aParseIsNamedParsedRetriedTemplateOrFailed() {
        FactsParser parser = new FactsParser();

        assertThat(Corpus.outcome(parser.parse("a", "FROM ubi9\n"))).isEqualTo("parsed");
        assertThat(Corpus.outcome(parser.parse("b", "FROM php:8.5\nCMD [\"php\" , \"-S\"]\n"))).isEqualTo("retried");
        assertThat(Corpus.outcome(parser.parse("c", "FROM {{ spec.base }}\n"))).isEqualTo("template");
        assertThat(Corpus.outcome(parser.parse("d", "not a dockerfile\n"))).isEqualTo("failed");
    }

    @Test
    void theRatchetMessageNamesTheCaseAndSaysWhatToDo() {
        assertThat(Corpus.ratchet("retried-json-comma", "parse"))
                .contains("retried-json-comma", "parse", "update expected.json");
    }
}
