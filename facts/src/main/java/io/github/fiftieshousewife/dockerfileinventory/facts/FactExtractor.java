package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.openrewrite.docker.DockerIsoVisitor;
import org.openrewrite.docker.tree.Docker;

/** Reduces a parsed Dockerfile to {@link ImageFacts}. With {@link FactsParser}, the only code that sees rewrite types. */
public final class FactExtractor {

    private static final java.util.regex.Pattern REFERENCE =
            java.util.regex.Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}|\\$([A-Za-z_][A-Za-z0-9_]*)");

    /** What a stage built {@code FROM} an earlier stage's name starts with. */
    private record Inherited(String user, List<String> ports, EntrypointForm entrypoint) {
        static final Inherited NONE = new Inherited(null, List.of(), EntrypointForm.NONE);
    }

    private static final Pattern DIRECTIVE = Pattern.compile("#\\s*(\\w+)\\s*=.*");

    /** By port number, then text, so {@code 53/udp} sorts before {@code 8080}; variables sort last. */
    private static final Comparator<String> PORT_ORDER = Comparator
            .comparingInt((String p) -> p.matches("\\d+.*") ? Integer.parseInt(p.replaceFirst("\\D.*", "")) : Integer.MAX_VALUE)
            .thenComparing(Comparator.naturalOrder());

    private FactExtractor() {
    }

    public static ImageFacts extract(Docker.File file) {
        Map<String, String> globalArgs = globalArgs(file);
        Map<String, Inherited> byStageName = new HashMap<>();
        RunTally runs = new RunTally();
        StageScan last = null;
        List<Docker.Stage> stages = file.getStages();
        for (int index = 0; index < stages.size(); index++) {
            Docker.Stage stage = stages.get(index);
            Docker.From from = stage.getFrom();
            last = new StageScan(byStageName.getOrDefault(text(from.getImageName(), globalArgs), Inherited.NONE), globalArgs);
            for (Docker.Instruction instruction : stage.getInstructions()) {
                last.accept(instruction);
                if (instruction instanceof Docker.Run run) {
                    runs.add(index, run, last.vars);
                }
            }
            Docker.From.As as = from.getAs();
            if (as != null) {
                byStageName.put(as.getName().getText(), last.result());
            }
        }
        Inherited end = last == null ? Inherited.NONE : last.result();
        return new ImageFacts(stages.size(), finalBase(file, globalArgs), runs.calls, runs.runs, runs.opaque,
                end.user(), kind(end.user()), end.ports(), end.entrypoint(),
                List.copyOf(buildKitFeatures(file)));
    }

    private static Map<String, String> globalArgs(Docker.File file) {
        Map<String, String> globalArgs = new HashMap<>();
        file.getGlobalArgs().forEach(arg -> argPairs(arg).forEach((name, value) -> {
            if (value != null && !ownName(value, name)) {
                globalArgs.put(name, substituted(value, globalArgs));
            }
        }));
        return globalArgs;
    }

    /**
     * An {@code ARG}'s names and their values as written, in order; null for a name without one. Read from the text,
     * as rewrite-docker 8.90.4 folds {@code ARG A=1 B=2} into one name whose value is {@code 1 B=2} (upstream issue
     * 12). Words split on whitespace outside quotes; quotes are removed.
     */
    static Map<String, String> argPairs(Docker.Arg arg) {
        Docker.Argument value = arg.getValue();
        String written = arg.getName().getText() + (value == null ? "" : "=" + text(value, Map.of()));
        Map<String, String> pairs = new java.util.LinkedHashMap<>();
        for (String word : words(written)) {
            int eq = word.indexOf('=');
            pairs.put(eq < 0 ? word : word.substring(0, eq), eq < 0 ? null : unquoted(word.substring(eq + 1)));
        }
        return pairs;
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        for (char c : text.toCharArray()) {
            if (quote == 0 && Character.isWhitespace(c)) {
                if (!word.isEmpty()) {
                    words.add(word.toString());
                    word.setLength(0);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = quote == 0 ? c : quote == c ? 0 : quote;
            }
            word.append(c);
        }
        if (!word.isEmpty()) {
            words.add(word.toString());
        }
        return words;
    }

    private static String unquoted(String value) {
        boolean quoted = value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"));
        return quoted ? value.substring(1, value.length() - 1) : value;
    }

    /** A default that is only the argument's own name ({@code A=$A}, how the parser's retry writes {@code ARG A B}). */
    private static boolean ownName(String value, String name) {
        return value.equals("$" + name) || value.equals("${" + name + "}");
    }

    /** {@code $NAME} and {@code ${NAME}} replaced from {@code vars}; an unknown one stays as written. */
    private static String substituted(String value, Map<String, String> vars) {
        return REFERENCE.matcher(value).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(
                vars.getOrDefault(m.group(1) != null ? m.group(1) : m.group(2), m.group())));
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
                case Docker.Env env -> env.getPairs().forEach(p -> vars.put(p.getKey().getText(), text(p.getValue(), vars)));
                case Docker.User u -> user = text(u.getUser(), vars);
                case Docker.Entrypoint e ->
                        entrypoint = e.getCommand() instanceof Docker.ExecForm ? EntrypointForm.EXEC : EntrypointForm.SHELL;
                case Docker.Expose expose -> expose.getPorts().forEach(p -> ports.add(p.getText().replaceFirst("/tcp$", "")));
                default -> {
                }
            }
        }

        /**
         * Each name an {@code ARG} declares: without a default (or one that is only its own name) it takes the global
         * default inside a stage, as Docker does.
         */
        private void arg(Docker.Arg arg) {
            argPairs(arg).forEach((name, declared) -> {
                String value = declared == null || ownName(declared, name) ? globalArgs.get(name)
                        : substituted(declared, vars);
                if (value != null) {
                    vars.put(name, value);
                }
            });
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

        /** Package variables expand from the stage's {@code ARG} and {@code ENV} and the RUN's own assignments. */
        void add(int stage, Docker.Run run, Map<String, String> stageVariables) {
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
        for (String line : text.lines().toList()) {
            var directive = DIRECTIVE.matcher(line);
            if (!directive.matches()) {
                return false;
            }
            if (directive.group(1).equalsIgnoreCase("syntax")) {
                return true;
            }
        }
        return false;
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
        return switch (run.getCommand()) {
            case Docker.ShellForm shell -> ShellTokeniser.parse(shell.getArgument().getText());
            case Docker.ExecForm exec -> Optional.of(List.of(exec.getArguments().stream().map(Docker.Literal::getText).toList()));
            case Docker.HeredocForm heredoc -> ShellTokeniser.parse(heredoc.getBodies().stream()
                    .flatMap(b -> b.getContentLines().stream())
                    .collect(Collectors.joining("\n")));
            default -> Optional.empty();
        };
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
