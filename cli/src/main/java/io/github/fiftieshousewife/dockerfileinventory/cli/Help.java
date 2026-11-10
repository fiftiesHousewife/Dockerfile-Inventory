package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.List;
import java.util.stream.Collectors;

final class Help {

    static final String USAGE = "usage: docker-inventory <command> [arguments], or <command> --help for its settings";

    private Help() {
    }

    static String commands(List<Command> commands) {
        int width = commands.stream().mapToInt(command -> command.name().length()).max().orElse(0);
        return USAGE + "\n\ncommands:\n" + commands.stream()
                .map(command -> "  " + pad(command.name(), width) + "  " + command.summary())
                .collect(Collectors.joining("\n"))
                + "\n\n  --version  the version\n  --help     this list\n"
                + "\nSettings come from the environment; see the README.\n";
    }

    static String command(Command command) {
        int width = command.settings().stream().mapToInt(setting -> setting.name().length()).max().orElse(0);
        return "docker-inventory " + command.name() + ": " + command.summary() + "\n\nsettings:\n"
                + command.settings().stream().map(setting -> "  " + pad(setting.name(), width) + "  "
                        + qualifier(setting) + setting.description()).collect(Collectors.joining("\n")) + "\n";
    }

    static String qualifier(Setting setting) {
        if (setting.required()) {
            return "required: ";
        }
        return setting.fallback() == null ? "optional: " : "optional, default " + setting.fallback() + ": ";
    }

    private static String pad(String text, int width) {
        return text + " ".repeat(width - text.length());
    }
}
