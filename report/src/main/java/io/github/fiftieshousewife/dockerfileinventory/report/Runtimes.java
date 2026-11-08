package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Java, Go, Node.js and Python in a Dockerfile: in a base's name, a package installed or the final image's
// environment, and whether each reaches the final image or stays in a build stage.
final class Runtimes {

    record Found(String runtime, String where, String evidence) {
    }

    private record Evidence(Pattern base, Pattern pkg, Set<String> environment) {
    }

    private static final Map<String, Evidence> EVIDENCE = new LinkedHashMap<>();

    static {
        EVIDENCE.put("java", new Evidence(Pattern.compile("openjdk|temurin|jdk|jre|java"),
                Pattern.compile("java-.*openjdk.*|openjdk.*|default-j(re|dk).*|temurin.*"),
                Set.of("JAVA_HOME", "JAVA_VERSION")));
        EVIDENCE.put("go", new Evidence(Pattern.compile("golang|go-toolset"), Pattern.compile("golang.*"),
                Set.of("GOPATH", "GOROOT", "GOLANG_VERSION")));
        EVIDENCE.put("node", new Evidence(Pattern.compile("nodejs|node"), Pattern.compile("nodejs.*|npm"),
                Set.of("NODE_VERSION", "NODEJS_VERSION")));
        EVIDENCE.put("python", new Evidence(Pattern.compile("python"), Pattern.compile("python[0-9.]*(-pip)?"),
                Set.of("PYTHON_VERSION", "PYTHONPATH", "PYTHONUNBUFFERED")));
    }

    private Runtimes() {
    }

    static List<Found> of(List<CatalogueEntry.StageEntry> stages, List<CatalogueEntry.PackageEntry> packages,
            Map<String, String> environment) {
        Set<Integer> finals = new HashSet<>(finalStages(stages));
        List<Found> found = new ArrayList<>();
        stages.stream().filter(s -> s.baseName() != null).forEach(s -> EVIDENCE.forEach((runtime, e) -> {
            if (e.base().matcher(lastSegment(s.baseName())).find()) {
                add(found, runtime, finals.contains(s.index()), "stage " + s.index() + " base " + s.baseName());
            }
        }));
        packages.forEach(p -> EVIDENCE.forEach((runtime, e) -> {
            if (e.pkg().matcher(p.name()).matches()) {
                add(found, runtime, finals.contains(p.stage()), "stage " + p.stage() + " installs " + p.name());
            }
        }));
        environment.keySet().forEach(name -> EVIDENCE.forEach((runtime, e) -> {
            if (e.environment().contains(name)) {
                add(found, runtime, true, "environment " + name);
            }
        }));
        return found;
    }

    static List<Found> inherited(String sha, List<CatalogueEntry> entries, Map<String, CatalogueLinks.Links> links) {
        Map<String, CatalogueEntry> bySha = new HashMap<>();
        entries.forEach(entry -> bySha.put(entry.blobSha(), entry));
        List<Found> found = new ArrayList<>();
        Footprint.through(sha, entries, links).through().stream().map(bySha::get)
                .filter(ancestor -> ancestor.build() != null)
                .forEach(ancestor -> of(ancestor.build().stages(), ancestor.build().packages(),
                        ancestor.image().environment()).stream().filter(f -> "final".equals(f.where()))
                        .filter(f -> found.stream().noneMatch(seen -> seen.runtime().equals(f.runtime())))
                        .forEach(f -> found.add(new Found(f.runtime(), "inherited",
                                "from " + ancestor.blobSha() + ": " + f.evidence()))));
        return found;
    }

    static List<Integer> finalStages(List<CatalogueEntry.StageEntry> stages) {
        List<Integer> indexes = new ArrayList<>();
        CatalogueEntry.StageEntry stage = stages.isEmpty() ? null : stages.getLast();
        while (stage != null && !indexes.contains(stage.index())) {
            indexes.add(stage.index());
            String parent = stage.fromStage();
            stage = parent == null ? null
                    : stages.stream().filter(s -> parent.equals(s.name())).findFirst().orElse(null);
        }
        return indexes;
    }

    private static void add(List<Found> found, String runtime, boolean inFinal, String evidence) {
        String where = inFinal ? "final" : "build";
        if (found.stream().noneMatch(f -> f.runtime().equals(runtime) && f.where().equals(where))) {
            found.add(new Found(runtime, where, evidence));
        }
    }

    private static String lastSegment(String image) {
        return image.substring(image.lastIndexOf('/') + 1);
    }
}
