package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;
import java.util.Objects;

/**
 * What a {@code RUN} does: install packages (always cleaned), shell commands joined by {@code &&}, a program in exec
 * form, or a script in a heredoc.
 */
public sealed interface RunBody {

    /** A package install the model reads and checks (H1). */
    record Install(PackageInstall install) implements RunBody {
    }

    /** Shell commands, joined in one shell-form {@code RUN}. */
    record Shell(List<String> commands) implements RunBody {

        public Shell {
            commands = List.copyOf(commands);
            if (commands.isEmpty()) {
                throw new IllegalArgumentException("a RUN needs at least one command");
            }
        }
    }

    /** An exec-form {@code RUN}, run without a shell. */
    record Exec(List<String> arguments) implements RunBody {

        public Exec {
            arguments = List.copyOf(Checks.notEmpty(arguments, "an exec RUN argument"));
        }
    }

    /**
     * {@code RUN command <<EOF}: the lines are the command's input, or the script when the command is empty;
     * {@code quoted} stops expansion in them.
     */
    record Heredoc(String command, String delimiter, boolean quoted, List<String> lines) implements RunBody {

        public Heredoc {
            Objects.requireNonNull(command, "a heredoc command, empty for none");
            Checks.notBlank(delimiter, "a heredoc delimiter");
            lines = List.copyOf(lines);
        }

        /** A script: quoted, under {@code EOF}, with no command, unless said otherwise. */
        public static Heredoc of(String... lines) {
            return new Heredoc("", "EOF", true, List.of(lines));
        }

        /** The command the heredoc feeds, such as {@code bash}; none runs the lines as a script. */
        public Heredoc command(String command) {
            return new Heredoc(command, delimiter, quoted, lines);
        }

        /** Ends at {@code delimiter} instead of {@code EOF}. */
        public Heredoc delimitedBy(String delimiter) {
            return new Heredoc(command, delimiter, quoted, lines);
        }

        /** Unquoted, so variables in the lines expand. */
        public Heredoc expanding() {
            return new Heredoc(command, delimiter, false, lines);
        }
    }
}
