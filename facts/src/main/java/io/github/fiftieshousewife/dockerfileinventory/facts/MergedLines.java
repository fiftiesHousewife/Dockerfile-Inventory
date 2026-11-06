package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.openrewrite.docker.DockerIsoVisitor;
import org.openrewrite.docker.tree.Docker;

/**
 * rewrite-docker 8.90.4 does not report a line it cannot recognise (a misspelt keyword, say): it appends the line to
 * the previous instruction's last argument (upstream issue 3). A literal can hold a newline only after a backslash
 * continuation, so a bare newline marks such a line.
 */
final class MergedLines {

    private MergedLines() {
    }

    /** The first line merged into an argument anywhere in the file. */
    static Optional<String> in(Docker.File file) {
        AtomicReference<String> found = new AtomicReference<>();
        new DockerIsoVisitor<Integer>() {
            @Override
            public Docker.Literal visitLiteral(Docker.Literal literal, Integer p) {
                if (found.get() == null) {
                    after(literal.getText().replace("\r\n", "\n")).ifPresent(found::set);
                }
                return literal;
            }
        }.visit(file, 0);
        return Optional.ofNullable(found.get());
    }

    /**
     * The first non-blank line that follows a bare newline. A comment line inside a continued instruction ends with
     * one too, as Docker drops such lines, so a newline that ends a comment line does not count.
     */
    static Optional<String> after(String text) {
        List<String> lines = List.of(text.split("\n", -1));
        return IntStream.range(0, lines.size() - 1)
                .filter(i -> endsBare(lines.get(i)))
                .mapToObj(i -> lines.subList(i + 1, lines.size()).stream().map(String::strip)
                        .filter(next -> !next.isEmpty()).findFirst().orElse(""))
                .findFirst();
    }

    private static boolean endsBare(String line) {
        return !line.stripTrailing().endsWith("\\") && !line.strip().startsWith("#");
    }
}
