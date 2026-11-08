package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfileinventory.dsl.DockerfileRenderer;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;

final class Regenerated {

    private Regenerated() {
    }

    static String of(String original) {
        if (!(new FactsParser().parse("regenerated", original) instanceof ParseResult.Parsed parsed)) {
            throw new IllegalArgumentException("the Dockerfile does not parse");
        }
        Docker.File file = parsed.file();
        return DockerfileRenderer.render(Convert.of(file).model(), RoundTrip.targetFor(file));
    }
}
