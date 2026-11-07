package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.ParseExceptionResult;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.docker.DockerParser;
import org.openrewrite.docker.tree.Docker;

/**
 * The one place that calls rewrite-docker's parser, which reports syntax errors to its execution context instead of
 * throwing; they are collected here so a failure is a finding, not a crash.
 */
public final class FactsParser {

    /** How a template's failure message starts, so rates can count templates apart from parser failures. */
    public static final String TEMPLATE = "a template, not a Dockerfile";

    private static final String PARSES_ANYWAY = "it parses, as its markers happen to be valid Dockerfile text";

    private final DockerParser parser = DockerParser.builder().build();

    /**
     * The parsed file, or the failure. A rejected file is parsed again with rewrite-docker's known bugs worked around,
     * every line number kept; a file that parses first time is left exactly as written. A template is a failure
     * either way.
     */
    public ParseResult parse(String blobSha, String text) {
        ParseResult first = attempt(blobSha, text);
        ParseResult parsed = first instanceof ParseResult.Parsed ? first : attempt(blobSha, RetryText.of(text));
        return switch (parsed) {
            case ParseResult.Parsed p -> Templates.jinja(text)
                    .map(line -> template(blobSha, line, PARSES_ANYWAY))
                    .orElse(p);
            case ParseResult.Failed _ -> Templates.anyMarker(text)
                    .map(line -> template(blobSha, line, "the parser said: " + ((ParseResult.Failed) first).message()))
                    .orElse(first);
        };
    }

    private static ParseResult template(String blobSha, String line, String detail) {
        return new ParseResult.Failed(blobSha, TEMPLATE + " (" + line + "); " + detail);
    }

    ParseResult attempt(String blobSha, String text) {
        var input = new Parser.Input(Path.of(blobSha, "Dockerfile"),
                () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        List<Throwable> errors = new ArrayList<>();
        List<SourceFile> files = parser.parseInputs(List.of(input), null, new InMemoryExecutionContext(errors::add))
                .toList();
        if (!errors.isEmpty()) {
            return new ParseResult.Failed(blobSha, errors.getFirst().getMessage());
        }
        if (!(files.getFirst() instanceof Docker.File file)) {
            return new ParseResult.Failed(blobSha, parseError(files.getFirst()));
        }
        return MergedLines.in(file)
                .<ParseResult>map(line -> new ParseResult.Failed(blobSha,
                        "unrecognised line merged into an argument: " + line))
                .orElseGet(() -> new ParseResult.Parsed(blobSha, file));
    }

    static String parseError(SourceFile source) {
        return source.getMarkers().findFirst(ParseExceptionResult.class)
                .map(ParseExceptionResult::getMessage)
                .map(message -> message.lines().findFirst().orElse(message))
                .orElse("the parser could not read the file");
    }
}
