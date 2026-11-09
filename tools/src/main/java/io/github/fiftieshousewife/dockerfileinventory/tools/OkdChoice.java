package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

// Which OKD images a lineage run copies: tools and network-tools (one built on the other), then one image per
// repository discovery scanned, spread evenly across the release, while they and every base they need stay within
// the count.
record OkdChoice(List<Image> candidates, List<Image> chosen, List<String> bases) {

    static final String CONTENT = "okd/scos-content";

    record Image(String tag, String repository, String commit) {
    }

    OkdChoice {
        candidates = List.copyOf(candidates);
        chosen = List.copyOf(chosen);
        bases = List.copyOf(bases);
    }

    static List<Image> candidates(String release, List<Image> images, Set<String> scanned, int count) {
        Set<String> tools = Set.of(release + "-tools", release + "-network-tools");
        Set<String> seen = new HashSet<>();
        List<Image> others = images.stream().filter(image -> !tools.contains(image.tag()))
                .filter(image -> scanned.contains(image.repository()) && seen.add(image.repository())).toList();
        double step = Math.max(1, (double) others.size() / count);
        List<Image> spread = new ArrayList<>();
        for (int i = 0; i * step < others.size(); i++) {
            spread.add(others.get((int) (i * step)));
        }
        return Stream.concat(images.stream().filter(image -> tools.contains(image.tag())), spread.stream()).toList();
    }

    static OkdChoice of(List<Image> candidates, Map<String, List<String>> bases, int count) {
        List<Image> chosen = new ArrayList<>();
        Set<String> needed = new TreeSet<>();
        for (Image candidate : candidates) {
            Set<String> with = new TreeSet<>(needed);
            with.addAll(bases.getOrDefault(candidate.tag(), List.of()));
            if (chosen.size() + 1 + with.size() > count) {
                break;
            }
            chosen.add(candidate);
            needed = with;
        }
        return new OkdChoice(candidates, chosen, List.copyOf(needed));
    }

    // Each base, then each chosen image, with the name it gets in JCR.
    List<String> mirror(String release) {
        return Stream.concat(bases.stream().map(base -> base + " " + jcrName(base)), chosen.stream()
                .map(image -> "quay.io/" + CONTENT + ":" + image.tag() + " okd/"
                        + image.tag().substring(release.length() + 1) + ":" + release)).toList();
    }

    // No registry host, and a digest written as a tag of its first twelve characters.
    static String jcrName(String image) {
        String reference = image.substring(image.indexOf('/') + 1);
        int at = reference.indexOf("@sha256:");
        return at < 0 ? reference : reference.substring(0, at) + ":sha256-" + reference.substring(at + 8, at + 20);
    }

    List<String> releases() {
        return chosen.stream().filter(image -> !image.repository().isEmpty() && !image.commit().isEmpty())
                .map(image -> image.repository() + "," + image.commit()).distinct().sorted().toList();
    }
}
