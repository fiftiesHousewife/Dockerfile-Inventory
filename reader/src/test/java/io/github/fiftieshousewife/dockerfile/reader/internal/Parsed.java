package io.github.fiftieshousewife.dockerfile.reader.internal;

import io.github.fiftieshousewife.dockerfile.parser.DockerParser;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class Parsed {

    private Parsed() {
    }

    static Docker.File of(String dockerfile) {
        return DockerParser.parseText(dockerfile);
    }

    static <T extends Docker.Instruction> T first(String dockerfile, Class<T> type) {
        return DockerParser.parseText(dockerfile).getStages().getLast().getInstructions().stream()
                .filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
