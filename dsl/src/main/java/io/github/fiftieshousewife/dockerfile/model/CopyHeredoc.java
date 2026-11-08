package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** {@code COPY <<EOF destination}: the lines become the file; {@code quoted} stops expansion in them. */
public record CopyHeredoc(String destination, String delimiter, boolean quoted, List<String> lines)
        implements Instruction {

    public CopyHeredoc {
        Checks.notBlank(destination, "a COPY destination");
        Checks.notBlank(delimiter, "a heredoc delimiter");
        lines = List.copyOf(lines);
    }

    /** {@code CopyHeredoc.to("/etc/app.conf").lines("a = 1")}: quoted, under {@code EOF}, unless said otherwise. */
    public static Destination to(String destination) {
        return new Destination(destination);
    }

    /** Ends at {@code delimiter} instead of {@code EOF}, for lines that hold {@code EOF}. */
    public CopyHeredoc delimitedBy(String delimiter) {
        return new CopyHeredoc(destination, delimiter, quoted, lines);
    }

    /** Unquoted, so variables in the lines expand. */
    public CopyHeredoc expanding() {
        return new CopyHeredoc(destination, delimiter, false, lines);
    }

    /** Where the file goes, waiting for its lines. */
    public record Destination(String destination) {

        /** The file's lines. */
        public CopyHeredoc lines(String... lines) {
            return new CopyHeredoc(destination, "EOF", true, List.of(lines));
        }
    }
}
