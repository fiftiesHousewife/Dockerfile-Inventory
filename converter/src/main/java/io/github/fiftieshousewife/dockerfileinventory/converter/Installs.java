package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageInstall;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellTokeniser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises a {@code RUN} that is exactly one install and its manager's clean, as the renderer writes it (Phase 5,
 * D1): {@code <rpm tool> install ... && <rpm tool> clean all}, {@code apk add --no-cache ...}, or
 * {@code apt-get update && apt-get install ... && rm -rf /var/lib/apt/lists/*}. Anything else is not an install,
 * so it stays shell text and the image is unchanged.
 */
final class Installs {

    private static final Map<String, PackageManager> RPM =
            Map.of("microdnf", PackageManager.MICRODNF, "dnf", PackageManager.DNF, "yum", PackageManager.YUM);
    /** Flags that take a separate argument: the word after them is not a package. */
    private static final Set<String> FLAGS_WITH_ARGUMENT =
            Set.of("--virtual", "-t", "--repo", "--enablerepo", "--disablerepo", "--setopt", "-c", "--config");
    private static final String APT_LISTS = "/var/lib/apt/lists/*";

    private Installs() {
    }

    static Optional<PackageInstall> of(String command) {
        if (command.contains("$") || command.contains(">") || command.contains("<")) {
            return Optional.empty();
        }
        Optional<List<List<String>>> parsed = ShellTokeniser.parse(command);
        if (parsed.isEmpty() || !eachCommandIsItsOwnWords(command, parsed.get())) {
            return Optional.empty();
        }
        List<List<String>> commands = parsed.get();
        if (commands.size() == 2 && rpm(commands.get(0), "install") && commands.get(1).size() == 3
                && commands.get(1).get(0).equals(commands.get(0).get(0))
                && commands.get(1).subList(1, 3).equals(List.of("clean", "all"))) {
            return install(RPM.get(commands.get(0).get(0)), commands.get(0).subList(2, commands.get(0).size()));
        }
        if (commands.size() == 1 && commands.get(0).size() > 2 && commands.get(0).subList(0, 2).equals(List.of("apk", "add"))
                && commands.get(0).contains("--no-cache")) {
            return install(PackageManager.APK, commands.get(0).subList(2, commands.get(0).size()));
        }
        if (commands.size() == 3 && commands.get(0).equals(List.of("apt-get", "update"))
                && commands.get(1).size() > 2 && commands.get(1).subList(0, 2).equals(List.of("apt-get", "install"))
                && commands.get(2).equals(List.of("rm", "-rf", APT_LISTS))) {
            return install(PackageManager.APT, commands.get(1).subList(2, commands.get(1).size()));
        }
        return Optional.empty();
    }

    private static boolean rpm(List<String> command, String subcommand) {
        return command.size() > 2 && RPM.containsKey(command.get(0)) && command.get(1).equals(subcommand);
    }

    /** Flags start with a hyphen; everything else is a package. A flag that takes an argument makes it unclear. */
    private static Optional<PackageInstall> install(PackageManager manager, List<String> words) {
        List<String> flags = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        for (String word : words) {
            if (FLAGS_WITH_ARGUMENT.contains(word)) {
                return Optional.empty();
            }
            (word.startsWith("-") ? flags : packages).add(word);
        }
        return packages.isEmpty() ? Optional.empty() : Optional.of(new PackageInstall(manager, flags, packages));
    }

    /**
     * The tokeniser drops leading assignments ({@code DEBIAN_FRONTEND=noninteractive}), which would lose them; so
     * each {@code &&}-separated part of the text must start with its command's first word.
     */
    private static boolean eachCommandIsItsOwnWords(String text, List<List<String>> commands) {
        String[] parts = text.split("&&");
        if (parts.length != commands.size()) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            if (commands.get(i).isEmpty() || !parts[i].strip().startsWith(commands.get(i).getFirst() + " ")) {
                return false;
            }
        }
        return true;
    }
}
