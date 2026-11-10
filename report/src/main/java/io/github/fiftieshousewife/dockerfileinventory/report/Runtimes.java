package io.github.fiftieshousewife.dockerfileinventory.report;

import static java.util.stream.Collectors.toMap;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class Runtimes {

    static final Map<String, String> PLACES = Map.of("final", "shipped", "build", "build only", "inherited",
            "from its base");

    static String label(Found found) {
        return found.runtime() + " (" + PLACES.getOrDefault(found.where(), found.where()) + ")";
    }

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
        Stream<Found> evidence = Stream.of(
                stages.stream().filter(stage -> stage.baseName() != null).flatMap(stage -> fromBase(stage, finals)),
                packages.stream().flatMap(installed -> fromPackage(installed, finals)),
                environment.keySet().stream().flatMap(Runtimes::fromEnvironment)).flatMap(found -> found);
        return firstOf(evidence, found -> List.of(found.runtime(), found.where()));
    }

    static Stream<Found> fromBase(CatalogueEntry.StageEntry stage, Set<Integer> finals) {
        return EVIDENCE.entrySet().stream()
                .filter(runtime -> runtime.getValue().base().matcher(lastSegment(stage.baseName())).find())
                .map(runtime -> found(runtime.getKey(), finals.contains(stage.index()),
                        "stage " + stage.index() + " base " + stage.baseName()));
    }

    static Stream<Found> fromPackage(CatalogueEntry.PackageEntry installed, Set<Integer> finals) {
        return EVIDENCE.entrySet().stream()
                .filter(runtime -> runtime.getValue().pkg().matcher(installed.name()).matches())
                .map(runtime -> found(runtime.getKey(), finals.contains(installed.stage()),
                        "stage " + installed.stage() + " installs " + installed.name()));
    }

    static Stream<Found> fromEnvironment(String name) {
        return EVIDENCE.entrySet().stream().filter(runtime -> runtime.getValue().environment().contains(name))
                .map(runtime -> found(runtime.getKey(), true, "environment " + name));
    }

    static Found found(String runtime, boolean inFinal, String evidence) {
        return new Found(runtime, inFinal ? "final" : "build", evidence);
    }

    static List<Found> inherited(List<String> ancestors, Map<String, CatalogueEntry> bySha) {
        return firstOf(ancestors.stream().map(bySha::get).filter(ancestor -> ancestor.build() != null)
                .flatMap(Runtimes::inheritedFrom), Found::runtime);
    }

    static Stream<Found> inheritedFrom(CatalogueEntry ancestor) {
        return of(ancestor.build().stages(), ancestor.build().packages(), ancestor.image().environment()).stream()
                .filter(found -> "final".equals(found.where()))
                .map(found -> new Found(found.runtime(), "inherited", "from " + ancestor.blobSha() + ": "
                        + found.evidence()));
    }

    static List<Found> firstOf(Stream<Found> found, Function<Found, Object> key) {
        return List.copyOf(found.collect(toMap(key, each -> each, (first, later) -> first, LinkedHashMap::new))
                .values());
    }

    static List<Integer> finalStages(List<CatalogueEntry.StageEntry> stages) {
        Set<Integer> seen = new HashSet<>();
        return Stream.iterate(stages.isEmpty() ? null : stages.getLast(),
                        stage -> stage != null && seen.add(stage.index()),
                        stage -> stage.fromStage() == null ? null
                                : CatalogueLinks.stageNamed(stages, stage.fromStage()))
                .map(CatalogueEntry.StageEntry::index).toList();
    }

    private static String lastSegment(String image) {
        return image.substring(image.lastIndexOf('/') + 1);
    }
}
