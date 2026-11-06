package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractor;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;

/** Parses a Dockerfile written in a test into the subject rules see. */
final class Subjects {

    private Subjects() {
    }

    static Subject of(String dockerfile) {
        return switch (new FactsParser().parse("blob", dockerfile)) {
            case ParseResult.Parsed parsed -> Subject.parsed("blob", FactExtractor.extract(parsed.file()), parsed.file());
            case ParseResult.Failed failed -> Subject.failed("blob", failed.message());
        };
    }
}
