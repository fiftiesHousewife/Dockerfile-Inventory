package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.parser.DockerParser;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

/**
 * Reads a Dockerfile into the model: every instruction typed, package installs read from {@code RUN}, and what
 * cannot be typed kept as written ({@code Raw}), with its reason, so rendering the model gives the Dockerfile back.
 */
public final class DockerfileReader {

    private DockerfileReader() {
    }

    /** The model of a Dockerfile's text; throws its first syntax error, with the line and column. */
    public static Conversion read(String dockerfile) {
        return read(DockerParser.parseText(dockerfile));
    }

    /** The model of an already parsed Dockerfile. */
    public static Conversion read(Docker.File dockerfile) {
        return Convert.of(dockerfile);
    }
}
