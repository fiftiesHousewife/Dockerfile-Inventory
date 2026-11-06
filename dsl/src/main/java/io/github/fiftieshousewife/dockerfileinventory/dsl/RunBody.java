package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

/** What a {@code RUN} does: install packages (rendered per manager, always cleaned), or commands joined by {@code &&}. */
public sealed interface RunBody {

    record Install(PackageInstall install) implements RunBody {
    }

    record Shell(List<String> commands) implements RunBody {

        public Shell {
            commands = List.copyOf(commands);
            if (commands.isEmpty()) {
                throw new IllegalArgumentException("a RUN needs at least one command");
            }
        }
    }
}
