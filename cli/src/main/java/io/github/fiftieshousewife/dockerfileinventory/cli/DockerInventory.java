package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The {@code docker-inventory} command: {@code survey} runs every stage in order; each stage also runs alone. Settings
 * come from the environment. Exits 0 when done, 1 when a stage fails, 2 on a usage error found before anything runs.
 */
public final class DockerInventory {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int USAGE = 2;

    private DockerInventory() {
    }

    /**
     * Runs the command the arguments name.
     *
     * @param args the command and its arguments
     */
    public static void main(String... args) {
        System.exit(launch(List.of(args), System.getenv(), System.out, System.err));
    }

    // The logging settings become system properties before anything logs, or Log4j starts without them.
    static int launch(List<String> args, Map<String, String> env, PrintStream out, PrintStream err) {
        try {
            Logging.of(args, env).properties().forEach(System::setProperty);
        } catch (UsageError e) {
            err.println(e.getMessage());
            return USAGE;
        }
        return run(Logging.withoutFlags(args), env, out, err, Commands.ALL);
    }

    static int run(List<String> args, Map<String, String> env, PrintStream out, PrintStream err,
                   List<Command> commands) {
        if (args.isEmpty()) {
            err.print(Help.commands(commands));
            return USAGE;
        }
        return switch (args.getFirst()) {
            case "--help", "-h", "help" -> printed(out, Help.commands(commands));
            case "--version" -> printed(out, "docker-inventory " + Version.current() + "\n");
            default -> command(args.getFirst(), commands)
                    .map(command -> run(command, args.subList(1, args.size()), env, out, err, commands))
                    .orElseGet(() -> {
                        err.println("unknown command: " + args.getFirst());
                        err.print(Help.commands(commands));
                        return USAGE;
                    });
        };
    }

    static int run(Command command, List<String> args, Map<String, String> env, PrintStream out, PrintStream err,
                   List<Command> commands) {
        if (args.contains("--help")) {
            return printed(out, Help.command(command));
        }
        if (!command.arguments() && !args.isEmpty()) {
            err.println(command.name() + ": unknown argument " + args.getFirst());
            return USAGE;
        }
        NearMisses.warnings(env.keySet(), known(commands)).forEach(err::println);
        Optional<Setting> missing = command.settings().stream()
                .filter(setting -> setting.required() && isBlank(env.get(setting.name()))).findFirst();
        if (missing.isPresent()) {
            err.println(command.name() + ": " + missing.get().name() + " is required: " + missing.get().description());
            return USAGE;
        }
        try {
            return command.runner().run(args, env);
        } catch (UsageError e) {
            err.println(command.name() + ": " + e.getMessage());
            return USAGE;
        } catch (Exception e) {
            err.println(command.name() + " failed: " + firstLine(e));
            return FAILED;
        }
    }

    static java.util.Set<String> known(List<Command> commands) {
        return commands.stream().flatMap(command -> command.settings().stream()).map(Setting::name)
                .collect(Collectors.toSet());
    }

    static String firstLine(Exception e) {
        return String.valueOf(e.getMessage()).lines().findFirst().orElse(e.getClass().getSimpleName());
    }

    private static Optional<Command> command(String name, List<Command> commands) {
        return commands.stream().filter(command -> command.name().equals(name)).findFirst();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int printed(PrintStream out, String text) {
        out.print(text);
        return DONE;
    }
}
