package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.parser.DockerParser;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class Parsed {

    private Parsed() {
    }

    static Docker.File of(String dockerfile) {
        return DockerParser.parseText(dockerfile);
    }
}
