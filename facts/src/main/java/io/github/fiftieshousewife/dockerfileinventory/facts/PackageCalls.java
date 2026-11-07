package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    /**
     * Base images' wrappers around a manager, as the command they run. minideb's {@code install_packages} runs
     * {@code apt-get install} and removes the lists after, so it is an apt install whose cache is cleaned (the
     * owner's choice, 7 Nov); {@code uninstall_packages} runs {@code apt-get remove}.
     */
    private record Wrapper(String manager, String subcommand, boolean cleans) {
    }

    private static final Map<String, Wrapper> WRAPPERS = Map.of(
            "install_packages", new Wrapper("apt-get", "install", true),
            "uninstall_packages", new Wrapper("apt-get", "remove", false));

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
                .filter(i -> MANAGERS.containsKey(commands.get(i).getFirst())
                        || WRAPPERS.containsKey(commands.get(i).getFirst()))
                .mapToObj(i -> call(commands.get(i), commands.subList(i + 1, commands.size())))
                .toList();
    }

    private static List<String> expand(List<String> command, Map<String, String> variables) {
        return command.stream().flatMap(word -> expandedWord(word, variables).stream()).toList();
    }

    /** A word that is only a variable's reference, as the words of its value when that is known. */
    private static List<String> expandedWord(String word, Map<String, String> variables) {
        Matcher variable = VARIABLE.matcher(word);
        String value = variable.matches() ? variables.get(variable.group(1)) : null;
        if (value == null || Expansions.holdsCommandSubstitution(value)) {
            return List.of(word);
        }
        return Arrays.stream(Continuations.removed(unquote(value)).trim().split("\\s+"))
                .filter(w -> !w.isEmpty()).toList();
    }

    private static String unquote(String value) {
        String v = value.strip();
        boolean quoted = v.length() >= 2
                && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"));
        return quoted ? v.substring(1, v.length() - 1) : v;
    }

    private static PackageCall call(List<String> command, List<List<String>> later) {
        Wrapper wrapper = WRAPPERS.get(command.getFirst());
        if (wrapper == null) {
            return managerCall(command, later);
        }
        PackageCall unwrapped = managerCall(unwrapped(wrapper, command), later);
        return new PackageCall(unwrapped.manager(), unwrapped.subcommand(), unwrapped.flags(), unwrapped.packages(),
                wrapper.cleans() || unwrapped.cleanedInSameRun());
    }

    /** A wrapper's command written as the manager's: {@code install_packages a} as {@code apt-get install a}. */
    private static List<String> unwrapped(Wrapper wrapper, List<String> command) {
        return Stream.concat(Stream.of(wrapper.manager(), wrapper.subcommand()),
                command.subList(1, command.size()).stream()).toList();
    }

    private static PackageCall managerCall(List<String> command, List<List<String>> later) {
        String manager = command.getFirst();
        Family family = MANAGERS.get(manager);
        Map<Boolean, List<String>> byFlag = command.subList(1, command.size()).stream()
                .collect(Collectors.partitioningBy(w -> w.startsWith("-")));
        List<String> flags = byFlag.get(true);
        List<String> words = byFlag.get(false);
        String subcommand = words.isEmpty() ? "" : words.getFirst();
        List<String> packages = INSTALLS.contains(subcommand)
                ? words.subList(1, words.size()).stream().filter(w -> !Expansions.holdsCommandSubstitution(w)).toList()
                : List.of();
        boolean cleaned = (family == Family.APK && flags.contains("--no-cache"))
                || later.stream().anyMatch(c -> cleans(c, family));
        return new PackageCall(manager, subcommand, flags, packages, cleaned);
    }

    private static boolean cleans(List<String> command, Family family) {
        String first = command.getFirst();
        if (MANAGERS.get(first) == family && family != Family.APT && command.contains("clean")) {
            return true;
        }
        return first.equals("rm")
                && command.stream().anyMatch(w -> family.cacheDirs.stream().anyMatch(w::startsWith));
    }
}
