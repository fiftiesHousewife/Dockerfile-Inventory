package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import org.openrewrite.docker.tree.Docker;

final class Parsed {

    private Parsed() {
    }

    static Docker.File of(String dockerfile) {
        return ((ParseResult.Parsed) new FactsParser().parse("blob", dockerfile)).file();
    }
}
