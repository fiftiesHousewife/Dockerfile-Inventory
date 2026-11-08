package io.github.fiftieshousewife.dockerfile.reader.internal;

import io.github.fiftieshousewife.dockerfile.model.PackageInstall;
import io.github.fiftieshousewife.dockerfile.model.PackageManager;
import io.github.fiftieshousewife.dockerfile.reader.internal.ShellTokeniser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Installs {

    private static final Map<String, PackageManager> RPM =
            Map.of("microdnf", PackageManager.MICRODNF, "dnf", PackageManager.DNF, "yum", PackageManager.YUM);
    private static final Set<String> FLAGS_WITH_ARGUMENT =
            Set.of("--virtual", "-t", "--repo", "--enablerepo", "--disablerepo", "--setopt", "-c", "--config");
    private static final String APT_LISTS = "/var/lib/apt/lists/*";

    private Installs() {
    }

    public static Optional<PackageInstall> of(String command) {
        if (command.contains("$") || command.contains(">") || command.contains("<")) {
            return Optional.empty();
        }
        return ShellTokeniser.parse(command)
                .filter(commands -> eachCommandIsItsOwnWords(command, commands))
                .flatMap(commands -> rpm(commands).or(() -> apk(commands)).or(() -> apt(commands)));
    }

    public static Optional<PackageInstall> rpm(List<List<String>> commands) {
        if (commands.size() != 2) {
            return Optional.empty();
        }
        List<String> install = commands.get(0);
        boolean shape = install.size() > 2 && RPM.containsKey(install.get(0)) && install.get(1).equals("install")
                && commands.get(1).equals(List.of(install.get(0), "clean", "all"));
        return shape ? install(RPM.get(install.get(0)), install.subList(2, install.size())) : Optional.empty();
    }

    public static Optional<PackageInstall> apk(List<List<String>> commands) {
        boolean shape = commands.size() == 1 && commands.get(0).size() > 2
                && commands.get(0).subList(0, 2).equals(List.of("apk", "add"))
                && commands.get(0).contains("--no-cache");
        return shape ? install(PackageManager.APK, commands.get(0).subList(2, commands.get(0).size()))
                : Optional.empty();
    }

    public static Optional<PackageInstall> apt(List<List<String>> commands) {
        boolean shape = commands.size() == 3 && commands.get(0).equals(List.of("apt-get", "update"))
                && commands.get(1).size() > 2 && commands.get(1).subList(0, 2).equals(List.of("apt-get", "install"))
                && commands.get(2).equals(List.of("rm", "-rf", APT_LISTS));
        return shape ? install(PackageManager.APT, commands.get(1).subList(2, commands.get(1).size()))
                : Optional.empty();
    }

    public static Optional<PackageInstall> install(PackageManager manager, List<String> words) {
        if (words.stream().anyMatch(FLAGS_WITH_ARGUMENT::contains)) {
            return Optional.empty();
        }
        Map<Boolean, List<String>> flags = words.stream().collect(Collectors.partitioningBy(w -> w.startsWith("-")));
        return flags.get(false).isEmpty() ? Optional.empty()
                : Optional.of(new PackageInstall(manager, flags.get(true), flags.get(false)));
    }

    // The tokeniser drops leading assignments (DEBIAN_FRONTEND=noninteractive), which the model would lose.
    public static boolean eachCommandIsItsOwnWords(String text, List<List<String>> commands) {
        String[] parts = text.split("&&");
        return parts.length == commands.size() && IntStream.range(0, parts.length).allMatch(i ->
                !commands.get(i).isEmpty() && parts[i].strip().startsWith(commands.get(i).getFirst() + " "));
    }
}
