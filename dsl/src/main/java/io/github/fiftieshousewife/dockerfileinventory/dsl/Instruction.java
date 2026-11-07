package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One Dockerfile instruction after {@code FROM}. Lists and maps are copied, so a built model cannot change. */
public sealed interface Instruction {

    record Arg(String name, String defaultValue) implements Instruction {

        public Arg {
            Checks.notBlank(name, "an ARG name");
        }
    }

    /**
     * Values as written inside double quotes: {@code $NAME} expands, {@code \$} is a dollar, {@code \\} a
     * backslash.
     */
    record Env(Map<String, String> values) implements Instruction {

        public Env {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    /**
     * Values as written inside double quotes: {@code $NAME} expands, {@code \$} is a dollar, {@code \\} a
     * backslash.
     */
    record Label(Map<String, String> values) implements Instruction {

        public Label {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    record Run(RunBody body, List<RunFlag> flags) implements Instruction {

        public Run {
            flags = List.copyOf(flags);
        }

        public Run(RunBody body) {
            this(body, List.of());
        }
    }

    /** {@code COPY}; {@code chown}, {@code fromStage} and {@code chmod} are optional. */
    record Copy(List<String> sources, String destination, String chown, String fromStage, String chmod, boolean link,
            boolean jsonForm) implements Instruction {

        public Copy {
            sources = List.copyOf(sources);
            Checks.notBlank(destination, "a COPY destination");
        }

        public Copy(List<String> sources, String destination, String chown, String fromStage) {
            this(sources, destination, chown, fromStage, null, false, false);
        }
    }

    /** {@code ADD}: a URL, a Git repository or an archive to unpack; {@code COPY} for anything else. */
    record Add(List<String> sources, String destination, String chown, String chmod, String checksum,
            boolean keepGitDir, boolean link, boolean jsonForm) implements Instruction {

        public Add {
            sources = List.copyOf(Checks.notEmpty(sources, "an ADD source"));
            Checks.notBlank(destination, "an ADD destination");
        }
    }

    /** {@code COPY <<EOF destination}: the lines become the file; {@code quoted} stops expansion in them. */
    record CopyHeredoc(String destination, String delimiter, boolean quoted, List<String> lines)
            implements Instruction {

        public CopyHeredoc {
            Checks.notBlank(destination, "a COPY destination");
            Checks.notBlank(delimiter, "a heredoc delimiter");
            lines = List.copyOf(lines);
        }
    }

    /**
     * {@code HEALTHCHECK}: options ({@code interval}, {@code timeout}, {@code start-period}, {@code start-interval},
     * {@code retries}) in the order written, then the command; {@link #NONE} turns off a base's check.
     */
    record Healthcheck(Map<String, String> options, List<String> command, CommandForm form) implements Instruction {

        public static final Healthcheck NONE = new Healthcheck(Map.of(), List.of(), CommandForm.EXEC);

        public Healthcheck {
            options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
            command = List.copyOf(command);
            Checks.healthcheck(options, command, form);
        }
    }

    /** {@code ONBUILD}: the instruction runs in images built {@code FROM} this one. */
    record Onbuild(Instruction instruction) implements Instruction {

        public Onbuild {
            if (instruction instanceof Onbuild || instruction instanceof Maintainer) {
                throw new IllegalArgumentException("ONBUILD cannot hold " + instruction);
            }
        }
    }

    record User(String user) implements Instruction {

        public User {
            Checks.notBlank(user, "a user");
        }
    }

    record Workdir(String path) implements Instruction {

        public Workdir {
            Checks.notBlank(path, "a working directory");
        }
    }

    record Expose(List<Port> ports) implements Instruction {

        public Expose {
            ports = List.copyOf(Checks.notEmpty(ports, "an EXPOSE port"));
        }
    }

    /** Exec form only; a shell-form entrypoint exists only as {@link Raw} (H2). */
    record Entrypoint(List<String> command) implements Instruction {

        public Entrypoint {
            command = List.copyOf(command);
        }
    }

    record Cmd(List<String> command) implements Instruction {

        public Cmd {
            command = List.copyOf(command);
        }
    }

    /** Exec form, as Docker requires for {@code SHELL}. */
    record Shell(List<String> command) implements Instruction {

        public Shell {
            command = List.copyOf(Checks.notEmpty(command, "a SHELL command"));
        }
    }

    /** {@code jsonForm}: written {@code VOLUME ["/data"]} rather than {@code VOLUME /data}, as the original was. */
    record Volume(List<String> paths, boolean jsonForm) implements Instruction {

        public Volume {
            paths = List.copyOf(Checks.notEmpty(paths, "a VOLUME path"));
        }
    }

    record StopSignal(String signal) implements Instruction {

        public StopSignal {
            Checks.notBlank(signal, "a stop signal");
        }
    }

    /** Deprecated by Docker in favour of a label; kept so a converted Dockerfile reads as it did. */
    record Maintainer(String name) implements Instruction {

        public Maintainer {
            Checks.notBlank(name, "a maintainer");
        }
    }

    /** Text the model cannot type, rendered as written; the reason is kept because every use is reviewed. */
    record Raw(String text, String reason) implements Instruction {

        public Raw {
            Checks.notBlank(text, "raw text");
            Checks.notBlank(reason, "the reason for raw text");
        }
    }
}
