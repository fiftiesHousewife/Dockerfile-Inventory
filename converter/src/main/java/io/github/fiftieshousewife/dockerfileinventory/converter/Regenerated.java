package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.model.DockerfileRenderer;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import java.util.List;

final class Regenerated {

    private Regenerated() {
    }

    static String of(String original) {
        Docker.File file = parsed(original);
        return DockerfileRenderer.render(Convert.of(file).model(), RoundTrip.targetFor(file));
    }

    static List<String> localSources(String original) {
        return LocalSources.of(Convert.of(parsed(original)).model());
    }

    static List<String> secretIds(String original) {
        return LocalSources.secretIds(Convert.of(parsed(original)).model());
    }

    private static Docker.File parsed(String original) {
        if (!(new FactsParser().parse("regenerated", original) instanceof ParseResult.Parsed parsed)) {
            throw new IllegalArgumentException("the Dockerfile does not parse");
        }
        return parsed.file();
    }
}
