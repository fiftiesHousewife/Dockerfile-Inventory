package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import io.github.fiftieshousewife.dockerfile.parser.DockerIsoVisitor;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

// rewrite-docker issue 3 (docs/upstream/): a line it cannot recognise is appended to the previous instruction's last
// argument instead of reported. A literal holds a newline only after a continuation, so a bare newline marks one.
final class MergedLines {

    private MergedLines() {
    }

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

    static Optional<String> after(String text) {
        List<String> lines = List.of(text.split("\n", -1));
        return IntStream.range(0, lines.size() - 1)
                .filter(i -> endsBare(lines.get(i)))
                .mapToObj(i -> lines.subList(i + 1, lines.size()).stream().map(String::strip)
                        .filter(next -> !next.isEmpty()).findFirst().orElse(""))
                .findFirst();
    }

    static boolean endsBare(String line) {
        return !line.stripTrailing().endsWith("\\") && !line.strip().startsWith("#");
    }
}
