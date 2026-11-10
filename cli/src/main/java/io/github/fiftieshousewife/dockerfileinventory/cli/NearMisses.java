package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// An environment holds many variables the CLI never reads; only one starting as a setting does, and within two edits
// of it, is taken for a typo.
final class NearMisses {

    private static final int MOST_EDITS = 2;

    private NearMisses() {
    }

    static List<String> warnings(Set<String> environment, Set<String> settings) {
        Set<String> prefixes = settings.stream().map(NearMisses::prefix).collect(Collectors.toSet());
        return environment.stream().sorted()
                .filter(name -> !settings.contains(name) && prefixes.contains(prefix(name)))
                .flatMap(name -> nearest(name, settings).stream()
                        .map(setting -> name + " is not a setting; did you mean " + setting + "?"))
                .toList();
    }

    static Optional<String> nearest(String name, Set<String> settings) {
        return settings.stream().filter(setting -> edits(name, setting) <= MOST_EDITS)
                .min(Comparator.comparingInt((String setting) -> edits(name, setting))
                        .thenComparing(Comparator.naturalOrder()));
    }

    static String prefix(String name) {
        int underscore = name.indexOf('_');
        return underscore < 0 ? name : name.substring(0, underscore);
    }

    static int edits(String a, String b) {
        int[] previous = IntStream.rangeClosed(0, b.length()).toArray();
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
            }
            previous = current;
        }
        return previous[b.length()];
    }
}
