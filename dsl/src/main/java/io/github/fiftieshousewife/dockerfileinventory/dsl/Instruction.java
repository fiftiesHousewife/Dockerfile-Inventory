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

    record Env(Map<String, String> values) implements Instruction {

        public Env {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    record Label(Map<String, String> values) implements Instruction {

        public Label {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    record Run(RunBody body) implements Instruction {
    }

    /** {@code COPY}; {@code chown} and {@code fromStage} are optional. */
    record Copy(List<String> sources, String destination, String chown, String fromStage) implements Instruction {

        public Copy {
            sources = List.copyOf(sources);
            Checks.notBlank(destination, "a COPY destination");
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

    record Expose(int port) implements Instruction {

        public Expose {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("a port is 1 to 65535, not " + port);
            }
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

    /** Text the model cannot type, rendered as written; every use is reported (Phase 4, D3). */
    record Raw(String text, String reason) implements Instruction {

        public Raw {
            Checks.notBlank(text, "raw text");
            Checks.notBlank(reason, "the reason for raw text");
        }
    }
}
