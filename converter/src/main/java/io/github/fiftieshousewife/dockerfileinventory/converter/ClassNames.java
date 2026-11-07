package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ClassNames {

    private ClassNames() {
    }

    static String packageName(Location location) {
        String owner = location.repository().split("/", 2)[0];
        return "images." + owner.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    static String className(Location location) {
        List<String> parts = new ArrayList<>(List.of(location.repository().split("/")));
        List<String> path = new ArrayList<>(List.of(location.path().split("/")));
        String file = path.removeLast();
        parts.addAll(path);
        if (file.contains(".")) {
            parts.add(file.substring(file.indexOf('.') + 1));
        }
        String name = parts.stream()
                .flatMap(p -> Stream.of(p.split("[^A-Za-z0-9]+")))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining());
        return (Character.isDigit(name.charAt(0)) ? "Image" + name : name) + "Image";
    }
}
