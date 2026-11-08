package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.reader.DockerfileReader;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;

final class CorpusOutcome {

    private CorpusOutcome() {
    }

    static String roundTrip(String text) {
        if (!(new FactsParser().parse("corpus", text) instanceof ParseResult.Parsed parsed)) {
            return "not-parsed";
        }
        try {
            return RoundTrip.check(text, DockerfileReader.read(parsed.file()).model()).passed() ? "passes" : "fails";
        } catch (IllegalArgumentException e) {
            return "fails";
        }
    }
}
