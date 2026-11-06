package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.openrewrite.docker.DockerIsoVisitor;
import org.openrewrite.docker.tree.Docker;

/**
 * Reduces a parsed Dockerfile to {@link ImageFacts}. With {@link FactsParser} and {@link Steps}, the only code that
 * sees rewrite types.
 */
public final class FactExtractor {

    private static final Set<String> OTHER_SHELLS = Set.of("powershell", "pwsh", "cmd");

    /** What a stage built {@code FROM} an earlier stage's name starts with. */
    private record Inherited(String user, List<String> ports, EntrypointForm entrypoint) {
        static final Inherited NONE = new Inherited(null, List.of(), EntrypointForm.NONE);
    }

    private static final Pattern DIRECTIVE = Pattern.compile("#\\s*(\\w+)\\s*=.*");

    /** By port number, then text, so {@code 53/udp} sorts before {@code 8080}; variables sort last. */
    private static final Comparator<String> PORT_ORDER =
            Comparator.comparingInt(FactExtractor::portNumber).thenComparing(Comparator.naturalOrder());

    private FactExtractor() {
    }

    public static ImageFacts extract(Docker.File file) {
        Map<String, String> globalArgs = globalArgs(file);
        Map<String, Inherited> byStageName = new HashMap<>();
        RunTally runs = new RunTally();
        Inherited end = Inherited.NONE;
        List<Docker.Stage> stages = file.getStages();
        for (int index = 0; index < stages.size(); index++) {
            end = scanned(index, stages.get(index), globalArgs, byStageName, runs);
        }
        return new ImageFacts(stages.size(), finalBase(file, globalArgs), runs.calls, runs.runs, runs.opaque,
                end.user(), kind(end.user()), end.ports(), end.entrypoint(),
                List.copyOf(buildKitFeatures(file)), runs.otherShell);
    }

    /**
     * One stage, starting from what the earlier stage it is built {@code FROM} by name ended with; its own end is
     * recorded under its name for the stages after it. Each {@code RUN} is tallied under the stage's {@code SHELL}.
     */
    private static Inherited scanned(int index, Docker.Stage stage, Map<String, String> globalArgs,
            Map<String, Inherited> byStageName, RunTally runs) {
        Docker.From from = stage.getFrom();
        StageScan scan = new StageScan(
                byStageName.getOrDefault(text(from.getImageName(), globalArgs), Inherited.NONE), globalArgs);
        boolean underOtherShell = false;
        for (Docker.Instruction instruction : stage.getInstructions()) {
            scan.accept(instruction);
            switch (instruction) {
                case Docker.Shell shell -> underOtherShell = otherShell(shell);
                case Docker.Run run -> runs.add(index, run, scan.vars, underOtherShell);
                default -> {
                }
            }
        }
        Inherited end = scan.result();
        Optional.ofNullable(from.getAs()).ifPresent(as -> byStageName.put(as.getName().getText(), end));
        return end;
    }

    private static int portNumber(String port) {
        return port.matches("\\d+.*") ? Integer.parseInt(port.replaceFirst("\\D.*", "")) : Integer.MAX_VALUE;
    }

    private static Map<String, String> globalArgs(Docker.File file) {
        Map<String, String> globalArgs = new HashMap<>();
        file.getGlobalArgs().forEach(arg -> argPairs(arg).forEach((name, value) -> {
            if (value != null) {
                globalArgs.put(name, Expansions.expanded(value, globalArgs));
            }
        }));
        return globalArgs;
    }

    /**
     * An {@code ARG}'s names and their values as written, in order; null for a name without one. Read from the text,
     * as rewrite-docker 8.90.4 folds {@code ARG A=1 B=2} into one name whose value is {@code 1 B=2} (upstream issue
     * 12). Words split on whitespace outside quotes; quotes are removed. A default that is only the name's own
     * reference ({@code A=$A}, how the parser's retry writes {@code ARG A B}) is no default.
     */
    public static Map<String, String> argPairs(Docker.Arg arg) {
        Docker.Argument value = arg.getValue();
        String written = Continuations.removed(arg.getName().getText()
                + (value == null ? "" : "=" + text(value, Map.of())));
        Map<String, String> pairs = new LinkedHashMap<>();
        KeyValuePairs.of(written).forEach(pair -> {
            String declared = pair.value() == null ? null : KeyValuePairs.unquoted(pair.value());
            pairs.put(pair.key(), declared == null || ownName(declared, pair.key()) ? null : declared);
        });
        return pairs;
    }

    /** A default that is only the argument's own name ({@code A=$A}, how the parser's retry writes {@code ARG A B}). */
    private static boolean ownName(String value, String name) {
        return value.equals("$" + name) || value.equals("${" + name + "}");
    }

    /** One stage's walk: its variable scope, and the user, entrypoint and ports it ends with. */
    private static final class StageScan {

        private final Map<String, String> globalArgs;
        private final Map<String, String> vars = new HashMap<>();
        private final Set<String> ports = new TreeSet<>(PORT_ORDER);
        private String user;
        private EntrypointForm entrypoint;

        StageScan(Inherited inherited, Map<String, String> globalArgs) {
            this.globalArgs = globalArgs;
            this.user = inherited.user();
            this.entrypoint = inherited.entrypoint();
            this.ports.addAll(inherited.ports());
        }

        void accept(Docker.Instruction instruction) {
            switch (instruction) {
                case Docker.Arg arg -> arg(arg);
                case Docker.Env env -> env.getPairs()
                        .forEach(pair -> vars.put(pair.getKey().getText(), text(pair.getValue(), vars)));
                case Docker.User u -> user = text(u.getUser(), vars);
                case Docker.Entrypoint e -> entrypoint =
                        e.getCommand() instanceof Docker.ExecForm ? EntrypointForm.EXEC : EntrypointForm.SHELL;
                case Docker.Expose expose -> expose.getPorts()
                        .forEach(port -> ports.add(port.getText().replaceFirst("/tcp$", "")));
                default -> {
                }
            }
        }

        /**
         * Each name an {@code ARG} declares: without a default it takes the global default inside a stage, as
         * Docker does.
         */
        private void arg(Docker.Arg arg) {
            argPairs(arg).forEach((name, declared) -> Optional
                    .ofNullable(declared == null ? globalArgs.get(name) : Expansions.expanded(declared, vars))
                    .ifPresent(value -> vars.put(name, value)));
        }

        Inherited result() {
            return new Inherited(user, List.copyOf(ports), entrypoint);
        }
    }

    /** Every {@code RUN}: its package calls by stage, and how many the tokeniser declined. */
    private static final class RunTally {

        private final List<StagedCall> calls = new ArrayList<>();
        private int runs;
        private int opaque;
        private int otherShell;

        /**
         * Package variables expand from the stage's {@code ARG} and {@code ENV} and the RUN's own assignments. A RUN
         * under another shell (PowerShell, cmd) is counted apart (the owner's choice, 6 Nov), neither read nor
         * unreadable.
         */
        void add(int stage, Docker.Run run, Map<String, String> stageVariables, boolean underOtherShell) {
            if (underOtherShell) {
                otherShell++;
                return;
            }
            runs++;
            Optional<List<List<String>>> commands = commands(run);
            if (commands.isEmpty()) {
                opaque++;
            }
            Map<String, String> variables = new HashMap<>(stageVariables);
            script(run).ifPresent(script -> variables.putAll(ShellTokeniser.assignments(script)));
            commands.ifPresent(c -> PackageCalls.of(c, variables)
                    .forEach(call -> calls.add(new StagedCall(stage, call))));
        }
    }

    /** A {@code SHELL} that is not a POSIX shell: PowerShell, pwsh or cmd, as Windows images use. */
    static boolean otherShell(Docker.Shell shell) {
        if (shell.getArguments().isEmpty()) {
            return false;
        }
        String program = text(shell.getArguments().getFirst(), Map.of()).replace("\"", "").replace("'", "")
                .strip().toLowerCase(Locale.ROOT);
        String name = program.substring(Math.max(program.lastIndexOf('/'), program.lastIndexOf('\\')) + 1);
        return OTHER_SHELLS.contains(name.replaceFirst("\\.exe$", ""));
    }

    /** The final stage's base; a stage built {@code FROM} an earlier stage's name takes that stage's base. */
    static BaseImage finalBase(Docker.File file, Map<String, String> args) {
        Map<String, BaseImage> byStageName = new HashMap<>();
        BaseImage base = null;
        for (Docker.Stage stage : file.getStages()) {
            BaseImage from = base(stage.getFrom(), args);
            base = from.tag() == null && from.digest() == null ? byStageName.getOrDefault(from.name(), from) : from;
            Docker.From.As as = stage.getFrom().getAs();
            if (as != null) {
                byStageName.put(as.getName().getText(), base);
            }
        }
        return base;
    }

    /**
     * The parser splits name, tag and digest before variables are known, so a {@code FROM ${BASE}} whose default
     * carries a tag would keep the tag in the name. The reference is rebuilt, substituted, then split here.
     */
    static BaseImage base(Docker.From from, Map<String, String> args) {
        Docker.Argument tag = from.getTag();
        Docker.Argument digest = from.getDigest();
        String reference = text(from.getImageName(), args)
                + (tag == null ? "" : ":" + text(tag, args))
                + (digest == null ? "" : "@" + text(digest, args));
        return split(reference);
    }

    static BaseImage split(String reference) {
        int at = reference.indexOf('@');
        String digest = at < 0 ? null : reference.substring(at + 1);
        String nameAndTag = at < 0 ? reference : reference.substring(0, at);
        int colon = nameAndTag.lastIndexOf(':');
        boolean hasTag = colon > nameAndTag.lastIndexOf('/');
        return new BaseImage(hasTag ? nameAndTag.substring(0, colon) : nameAndTag,
                hasTag ? nameAndTag.substring(colon + 1) : null, digest);
    }

    /** Features a builder without BuildKit cannot run: heredocs, {@code RUN --mount}, a {@code # syntax=} directive. */
    static Set<String> buildKitFeatures(Docker.File file) {
        Set<String> features = new TreeSet<>();
        new DockerIsoVisitor<Integer>() {
            @Override
            public Docker.HeredocForm visitHeredocForm(Docker.HeredocForm heredoc, Integer p) {
                features.add("heredoc");
                return heredoc;
            }

            @Override
            public Docker.Run visitRun(Docker.Run run, Integer p) {
                List<Docker.Flag> flags = run.getFlags();
                if (flags != null && flags.stream().anyMatch(f -> f.getName().replace("-", "").equals("mount"))) {
                    features.add("run-mount");
                }
                return super.visitRun(run, p);
            }
        }.visit(file, 0);
        if (hasSyntaxDirective(file.printAll())) {
            features.add("syntax-directive");
        }
        return features;
    }

    /** Docker reads parser directives only from the leading {@code # key=value} lines, before any other line. */
    static boolean hasSyntaxDirective(String text) {
        return text.lines().map(DIRECTIVE::matcher).takeWhile(Matcher::matches)
                .anyMatch(directive -> directive.group(1).equalsIgnoreCase("syntax"));
    }

    static UserKind kind(String user) {
        if (user == null || user.equals("root") || user.equals("0")) {
            return UserKind.ROOT;
        }
        if (user.contains("${")) {
            return UserKind.UNRESOLVED;
        }
        return user.chars().allMatch(Character::isDigit) ? UserKind.NUMERIC : UserKind.NAMED;
    }

    /** A shell-form or heredoc RUN's script text; exec form has none. */
    static Optional<String> script(Docker.Run run) {
        return switch (run.getCommand()) {
            case Docker.ShellForm shell -> Optional.of(shell.getArgument().getText());
            case Docker.HeredocForm heredoc -> Optional.of(heredoc.getBodies().stream()
                    .flatMap(b -> b.getContentLines().stream())
                    .collect(Collectors.joining("\n")));
            default -> Optional.empty();
        };
    }

    /**
     * A {@code RUN}'s simple commands: the tokenised shell form or heredoc script, or the exec form's words as one
     * command. Empty when the tokeniser declines the script.
     */
    static Optional<List<List<String>>> commands(Docker.Run run) {
        return run.getCommand() instanceof Docker.ExecForm exec
                ? Optional.of(List.of(exec.getArguments().stream().map(Docker.Literal::getText).toList()))
                : script(run).flatMap(ShellTokeniser::parse);
    }

    /** An argument's text with known variables substituted; an unknown one is kept as {@code ${NAME}}. */
    private static String text(Docker.Argument argument, Map<String, String> args) {
        return argument.getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText() : variable((Docker.EnvironmentVariable) c, args))
                .collect(Collectors.joining());
    }

    private static String variable(Docker.EnvironmentVariable variable, Map<String, String> args) {
        return args.getOrDefault(variable.getName(), "${" + variable.getName() + "}");
    }
}
