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
import java.util.stream.Collectors;
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
    /** Jinja only: a block tag, or {@code {{ name.path }}}. Go's {@code {{.Id}}} and {@code {{ index .X }}} differ. */
    private static final Pattern JINJA =
            Pattern.compile("\\{%.*%}|\\{\\{\\s+([A-Za-z_]\\w*)(\\.[A-Za-z_]\\w*)*\\s*(\\|[^}]*)?}}");
    private static final java.util.Set<String> GO_KEYWORDS = java.util.Set.of(
            "end", "else", "if", "range", "with", "define", "template", "block", "break", "continue");
    private static final Pattern SPACE_BEFORE_COMMA = Pattern.compile("\"[ \\t]+,");
    private static final Pattern FLAG_DEFAULT =
            Pattern.compile("(--[A-Za-z-]+=\\S*?\\$\\{[A-Za-z_][A-Za-z0-9_]*):-");
    /** A line of an {@code ARG}: its keyword (absent on a continued line), its words, a trailing backslash. */
    private static final Pattern ARG_LINE = Pattern.compile("(?i)(\\s*ARG\\s+)?\\s*(\\S.*?)(\\s*\\\\)?\\s*");
    /** A plain name, or a name with a value that holds no quote, backslash or space. */
    private static final Pattern SIMPLE_ARG_WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(=[^\"'\\\\\\s]*)?");
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
        ParseResult parsed = first instanceof ParseResult.Parsed ? first : attempt(blobSha, retryText(text));
        if (parsed instanceof ParseResult.Parsed) {
            return jinja(text).<ParseResult>map(line -> new ParseResult.Failed(blobSha, TEMPLATE + " (" + line
                    + "); it parses, as its markers happen to be valid Dockerfile text")).orElse(parsed);
        }
        return template(text)
                .<ParseResult>map(line -> new ParseResult.Failed(blobSha, TEMPLATE + " (" + line + "); the parser said: "
                        + ((ParseResult.Failed) first).message()))
                .orElse(first);
    }

    /**
     * The first line holding Jinja's markers, which make even a file that parses a template: Jinja's
     * {@code {{ spec.version }}} is valid in a {@code FROM} or an {@code ENV}, but no real Dockerfile holds it.
     * Comment lines do not count, nor do a GitHub Actions expression ({@code ${{ github.sha }}}) or a Go template
     * keyword ({@code {{ end }}}), which real Dockerfiles do hold.
     */
    static Optional<String> jinja(String text) {
        return text.lines().filter(line -> !line.strip().startsWith("#") && jinjaMarker(line))
                .map(String::strip).findFirst();
    }

    private static boolean jinjaMarker(String line) {
        var marker = JINJA.matcher(line);
        while (marker.find()) {
            boolean actions = marker.start() > 0 && line.charAt(marker.start() - 1) == '$';
            if (!actions && (marker.group(1) == null || !GO_KEYWORDS.contains(marker.group(1)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first line holding Jinja or Go template markers ({@code {% if %}}, {@code {{ spec.version }}}). Asked only
     * of a file that failed to parse, so a {@code docker inspect --format '{{.Id}}'} in a valid file is not one.
     */
    static Optional<String> template(String text) {
        return text.lines().filter(line -> TEMPLATE_MARKER.matcher(line).find()).map(String::strip).findFirst();
    }

    /**
     * The text the retry parses: whole-line comments blanked, and, on lines holding a JSON array, the whitespace
     * before a comma dropped, which rewrite-docker 8.90.4 cannot print back ({@code CMD ["a" , "b"]}); and in a
     * flag's value, {@code ${NAME:-default}} written {@code ${NAME-default}}, which it can (the two differ only
     * when the variable is set but empty); and {@code ARG A B}, several names without values, which it rejects,
     * written {@code ARG A=$A B=$B}, so each value stays unknown as before. Only an instruction's own lines are
     * touched, never a heredoc body; {@code ARG A B} only when every word is a plain name and the line is not
     * continued. All keep every line where it was.
     */
    static String retryText(String text) {
        List<String> lines = new ArrayList<>(text.lines().toList());
        String heredoc = null;
        boolean inArg = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (heredoc != null) {
                heredoc = line.strip().equals(heredoc) ? null : heredoc;
                continue;
            }
            String rewritten = FLAG_DEFAULT.matcher(line.contains("[")
                    ? SPACE_BEFORE_COMMA.matcher(line).replaceAll("\",") : line).replaceAll("$1-");
            var arg = ARG_LINE.matcher(rewritten);
            boolean argLine = arg.matches() && (inArg || arg.group(1) != null);
            if (argLine) {
                rewritten = argNames(arg);
            }
            inArg = argLine && arg.group(3) != null;
            lines.set(i, rewritten);
            var opens = HEREDOC.matcher(line);
            heredoc = opens.find() ? opens.group(1) : null;
        }
        return String.join("\n", lines).concat(text.endsWith("\n") ? "\n" : "").transform(FactsParser::withoutComments);
    }

    /**
     * One line of an {@code ARG}, its first or a continued one: when every word is a plain name or a simple
     * {@code NAME=value}, each plain name is given {@code =$NAME}; the trailing backslash is kept.
     */
    private static String argNames(java.util.regex.Matcher arg) {
        String[] words = arg.group(2).trim().split("\\s+");
        if (!java.util.Arrays.stream(words).allMatch(w -> SIMPLE_ARG_WORD.matcher(w).matches())) {
            return arg.group();
        }
        return arg.group(0).substring(0, arg.start(2)) + java.util.Arrays.stream(words)
                .map(w -> w.contains("=") ? w : w + "=$" + w)
                .collect(Collectors.joining(" ")) + (arg.group(3) == null ? "" : arg.group(3));
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
