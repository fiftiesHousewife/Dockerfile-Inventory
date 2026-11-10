package io.github.fiftieshousewife.dockerfileinventory.cli;

import io.github.fiftieshousewife.dockerfileinventory.discovery.RunStopped;
import io.github.fiftieshousewife.dockerfileinventory.discovery.SelectionNotFound;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The {@code docker-inventory} command: {@code survey} runs every stage in order; each stage also runs alone. Settings
 * come from the environment. Exits 0 when done, 1 when a stage fails, 2 on a usage error found before anything is
 * fetched, 3 when stopped on purpose (declined, or over the ceiling on Dockerfiles).
 */
public final class DockerInventory {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int USAGE = 2;
    static final int STOPPED = 3;

    private DockerInventory() {
    }

    /**
     * Runs the command the arguments name.
     *
     * @param args the command and its arguments
     */
    public static void main(String... args) {
        System.exit(launch(List.of(args), System.getenv(), System.out, System.err, Commands.ALL));
    }

    // The logging settings become system properties before anything logs, or Log4j starts without them.
    static int launch(List<String> args, Map<String, String> env, PrintStream out, PrintStream err,
                      List<Command> commands) {
        try {
            Flags flags = Flags.of(args);
            Logging.of(flags.rest(), env).properties().forEach(System::setProperty);
            Map<String, String> file = flags.settings().map(Path::of).map(SettingsFile::read).orElse(Map.of());
            Map<String, String> settings = new HashMap<>(SettingsFile.merged(file, env));
            settings.putAll(flags.environment());
            return run(Logging.withoutFlags(flags.rest()), Load.expanded(settings, flags.load()), out, err,
                    commands);
        } catch (UsageError e) {
            err.println(e.getMessage());
            return USAGE;
        }
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
                .filter(setting -> required(setting, env) && isBlank(env.get(setting.name()))).findFirst();
        if (missing.isPresent()) {
            err.println(command.name() + ": " + missing.get().name() + " is required: " + missing.get().description());
            return USAGE;
        }
        try {
            return command.runner().run(args, env);
        } catch (UsageError | SelectionNotFound e) {
            err.println(command.name() + ": " + e.getMessage());
            return USAGE;
        } catch (RunStopped e) {
            err.println(command.name() + ": " + e.getMessage());
            return STOPPED;
        } catch (Exception e) {
            err.println(command.name() + " failed: " + firstLine(e));
            return FAILED;
        }
    }

    static boolean required(Setting setting, Map<String, String> env) {
        return setting.required() || setting.requiredWith() != null && !isBlank(env.get(setting.requiredWith()))
                || setting.requiredUnless() != null && isBlank(env.get(setting.requiredUnless()));
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
