package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// What two builds of the same original differ in. A file only in one image is matched by its directory, as names
// that carry a process ID differ every build.
final class Noise {

    private static final String ONLY_IN = "only in";

    private final Set<String> differences;
    private final Set<String> directories;

    private Noise(Set<String> differences, Set<String> directories) {
        this.differences = differences;
        this.directories = directories;
    }

    static Noise of(List<String> control) {
        return new Noise(control.stream().map(Noise::key).collect(Collectors.toSet()),
                control.stream().filter(Noise::onlyInOne).map(Noise::directory).collect(Collectors.toSet()));
    }

    List<String> removedFrom(List<String> differences) {
        return differences.stream()
                .filter(difference -> !this.differences.contains(key(difference)))
                .filter(difference -> !onlyInOne(difference) || !directories.contains(directory(difference)))
                .toList();
    }

    // A file's path and what about it differs ("mode", "content"); a config field's name.
    static String key(String difference) {
        int colon = difference.indexOf(": ");
        if (colon < 0) {
            return difference;
        }
        if (!difference.startsWith("/")) {
            return difference.substring(0, colon);
        }
        String rest = difference.substring(colon + 2);
        return difference.substring(0, colon) + ": " + rest.split(" ", 2)[0];
    }

    static boolean onlyInOne(String difference) {
        return difference.startsWith("/") && difference.contains(": " + ONLY_IN + " ");
    }

    static String directory(String difference) {
        String path = difference.substring(0, difference.indexOf(": "));
        return path.substring(0, Math.max(1, path.lastIndexOf('/')));
    }
}
