package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.ParseExceptionResult;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.docker.DockerIsoVisitor;
import org.openrewrite.docker.DockerParser;
import org.openrewrite.docker.tree.Docker;

/**
 * The one place that calls rewrite-docker's parser. Syntax errors do not throw: the parser reports them to the
 * execution context's error handler and still returns a tree, so they are collected here.
 */
public final class FactsParser {


    /** How a template's failure message starts, so rates can count templates apart from parser failures. */
    public static final String TEMPLATE = "a template, not a Dockerfile";

    private static final Pattern TEMPLATE_MARKER = Pattern.compile("\\{%.*%}|\\{\\{.*}}");
    private static final Pattern HEREDOC = Pattern.compile("<<-?['\"]?(\\w+)");

    private final DockerParser parser = DockerParser.builder().build();

    /**
     * Parses the text; if rewrite-docker rejects it, parses it again with whole-line comments blanked, as Docker
     * drops them before reading an instruction. rewrite-docker 8.90.4 rejects some valid Dockerfiles over comments
     * (an apostrophe in a comment inside a continued {@code RUN}; a {@code # key=value} comment after the first
     * instruction, read as a parser directive; a line holding only a backslash), so this rescues
     * them, keeping every line number. A file that parses first time keeps its comments.
     */
    public ParseResult parse(String blobSha, String text) {
        ParseResult first = attempt(blobSha, text);
        if (first instanceof ParseResult.Parsed) {
            return first;
        }
        ParseResult second = attempt(blobSha, withoutComments(text));
        if (second instanceof ParseResult.Parsed) {
            return second;
        }
        return template(text)
                .<ParseResult>map(line -> new ParseResult.Failed(blobSha, TEMPLATE + " (" + line + "); the parser said: "
                        + ((ParseResult.Failed) first).message()))
                .orElse(first);
    }

    /**
     * The first line holding Jinja or Go template markers ({@code {% if %}}, {@code {{ spec.version }}}). Asked only
     * of a file that failed to parse, so a {@code docker inspect --format '{{.Id}}'} in a valid file is never one.
     */
    static Optional<String> template(String text) {
        return text.lines().filter(line -> TEMPLATE_MARKER.matcher(line).find()).map(String::strip).findFirst();
    }

    /**
     * Whole-line comments blanked, keeping the line count: inside a continued instruction a comment becomes a lone
     * backslash, which keeps the instruction going as Docker does, and elsewhere an empty line. The leading comment
     * block (where parser directives such as {@code # syntax=} live) and heredoc bodies (whose lines are the
     * script's) are left as they are. A line holding only a backslash outside an instruction is blanked too: Docker
     * reads nothing from it, but rewrite-docker glues it onto the instruction before.
     */
    static String withoutComments(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        boolean leading = true;
        boolean continued = false;
        String heredocEnd = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String stripped = line.strip();
            if (heredocEnd != null) {
                if (stripped.equals(heredocEnd)) {
                    heredocEnd = null;
                }
                continue;
            }
            if (stripped.startsWith("#")) {
                if (!leading) {
                    lines.set(i, continued ? "\\" : "");
                }
                continue;
            }
            if (stripped.equals("\\") && !continued) {
                lines.set(i, "");
                continue;
            }
            if (!stripped.isEmpty()) {
                leading = false;
            }
            continued = stripped.endsWith("\\");
            Matcher heredoc = HEREDOC.matcher(line);
            if (heredoc.find()) {
                heredocEnd = heredoc.group(1);
            }
        }
        return String.join("\n", lines);
    }

    private ParseResult attempt(String blobSha, String text) {
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
        return gluedLine(file)
                .<ParseResult>map(line -> new ParseResult.Failed(blobSha,
                        "unrecognised line merged into an argument: " + line))
                .orElseGet(() -> new ParseResult.Parsed(blobSha, file));
    }

    /**
     * rewrite-docker 8.90.4 does not report a line it cannot recognise (a misspelt keyword, say): it appends the
     * line to the previous instruction's last argument. A literal can hold a newline only after a backslash
     * continuation, so a bare newline marks such a line.
     */
    static Optional<String> gluedLine(Docker.File file) {
        AtomicReference<String> found = new AtomicReference<>();
        new DockerIsoVisitor<Integer>() {
            @Override
            public Docker.Literal visitLiteral(Docker.Literal literal, Integer p) {
                if (found.get() == null) {
                    gluedAfter(literal.getText().replace("\r\n", "\n")).ifPresent(found::set);
                }
                return literal;
            }
        }.visit(file, 0);
        return Optional.ofNullable(found.get());
    }

    /** A source the parser gave up on (it could not print the input back exactly), as its recorded message. */
    private static String parseError(SourceFile source) {
        return source.getMarkers().findFirst(ParseExceptionResult.class)
                .map(ParseExceptionResult::getMessage)
                .map(message -> message.lines().findFirst().orElse(message))
                .orElse("the parser could not read the file");
    }

    /**
     * The first non-blank line that follows a bare newline. A comment line inside a continued instruction ends with one too,
     * as Docker drops such lines, so a newline that ends a comment line does not count.
     */
    static Optional<String> gluedAfter(String text) {
        List<String> lines = List.of(text.split("\n", -1));
        for (int i = 0; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            if (!line.stripTrailing().endsWith("\\") && !line.strip().startsWith("#")) {
                return lines.subList(i + 1, lines.size()).stream()
                        .map(String::strip)
                        .filter(next -> !next.isEmpty())
                        .findFirst()
                        .or(() -> Optional.of(""));
            }
        }
        return Optional.empty();
    }
}
