package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Finds package-manager calls among a {@code RUN}'s commands, and whether each one's cache is cleaned later in the
 * same {@code RUN}: by the manager's own clean, or by removing the cache directory. {@code dnf}, {@code yum} and
 * {@code microdnf} share the RPM caches; {@code apk --no-cache} keeps no cache at all.
 */
public final class PackageCalls {

    private enum Family {
        RPM(List.of("/var/cache/dnf", "/var/cache/yum", "/var/cache/{")),
        APT(List.of("/var/lib/apt/lists")),
        APK(List.of("/var/cache/apk"));

        final List<String> cacheDirs;

        Family(List<String> cacheDirs) {
            this.cacheDirs = cacheDirs;
        }
    }

    /** Subcommands that add packages to the image; other subcommands' arguments are not counted as packages. */
    private static final Set<String> INSTALLS = Set.of("install", "add", "reinstall");

    private static final Map<String, Family> MANAGERS = Map.of(
            "microdnf", Family.RPM, "dnf", Family.RPM, "yum", Family.RPM,
            "apt-get", Family.APT, "apt", Family.APT,
            "apk", Family.APK);

    /** A value read from an {@code ENV} keeps its backslash-newlines; Docker removes them. */
    private static final Pattern CONTINUATION = Pattern.compile("\\\\[ \\t]*\\r?\\n");
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{?([A-Za-z_][A-Za-z0-9_]*)}?");

    private PackageCalls() {
    }

    public static List<PackageCall> of(List<List<String>> commands) {
        return of(commands, Map.of());
    }

    /**
     * Package calls with any word that is exactly {@code $NAME} or {@code ${NAME}} replaced by the words of that
     * variable's value, from {@code variables}; an unknown variable, or one whose value is a command substitution,
     * stays as written.
     */
    public static List<PackageCall> of(List<List<String>> unexpanded, Map<String, String> variables) {
        List<List<String>> commands = unexpanded.stream().map(c -> expand(c, variables)).toList();
        return IntStream.range(0, commands.size())
                .filter(i -> MANAGERS.containsKey(commands.get(i).getFirst()))
                .mapToObj(i -> call(commands.get(i), commands.subList(i + 1, commands.size())))
                .toList();
    }

    private static List<String> expand(List<String> command, Map<String, String> variables) {
        List<String> words = new ArrayList<>();
        for (String word : command) {
            var variable = VARIABLE.matcher(word);
            String value = variable.matches() ? variables.get(variable.group(1)) : null;
            if (value == null || substituted(value)) {
                words.add(word);
            } else {
                Arrays.stream(CONTINUATION.matcher(unquote(value)).replaceAll(" ").trim().split("\\s+"))
                        .filter(w -> !w.isEmpty())
                        .forEach(words::add);
            }
        }
        return words;
    }

    /** A word holding a command substitution: its value is only known when the image is built. */
    static boolean substituted(String word) {
        return word.contains("$(") || word.contains("`");
    }

    private static String unquote(String value) {
        String v = value.strip();
        boolean quoted = v.length() >= 2
                && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"));
        return quoted ? v.substring(1, v.length() - 1) : v;
    }

    private static PackageCall call(List<String> command, List<List<String>> later) {
        String manager = command.getFirst();
        Family family = MANAGERS.get(manager);
        List<String> flags = new ArrayList<>();
        List<String> words = new ArrayList<>();
        command.subList(1, command.size()).forEach(w -> (w.startsWith("-") ? flags : words).add(w));
        String subcommand = words.isEmpty() ? "" : words.getFirst();
        List<String> packages = INSTALLS.contains(subcommand)
                ? words.subList(1, words.size()).stream().filter(w -> !substituted(w)).toList() : List.of();
        boolean cleaned = (family == Family.APK && flags.contains("--no-cache"))
                || later.stream().anyMatch(c -> cleans(c, family));
        return new PackageCall(manager, subcommand, flags, packages, cleaned);
    }

    private static boolean cleans(List<String> command, Family family) {
        String first = command.getFirst();
        if (MANAGERS.get(first) == family && family != Family.APT && command.contains("clean")) {
            return true;
        }
        return first.equals("rm") && command.stream().anyMatch(w -> family.cacheDirs.stream().anyMatch(w::startsWith));
    }
}
