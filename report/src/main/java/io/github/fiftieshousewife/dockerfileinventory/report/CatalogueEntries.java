package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

final class CatalogueEntries {

    private CatalogueEntries() {
    }

    static CatalogueEntry of(JsonNode row, List<CatalogueEntry.Place> places) {
        return new CatalogueEntry(row.path("blobSha").asString(), places == null ? List.of() : places,
                Estate.metadataOnly(row), image(row), build(row), null, null, null, null, null);
    }

    static CatalogueEntry unparsed(JsonNode failure, List<CatalogueEntry.Place> places) {
        return new CatalogueEntry(failure.path("blobSha").asString(), places == null ? List.of() : places, false,
                null, null, null, null, null, null, failure.path("message").asString());
    }

    static Map<String, List<CatalogueEntry.Place>> places(Path discoveryDir) {
        Map<String, JsonNode> changes = new HashMap<>();
        JsonLines.read(discoveryDir.resolve("changes.jsonl")).forEach(row -> changes.put(location(row), row));
        Map<String, List<CatalogueEntry.Place>> places = new HashMap<>();
        JsonLines.read(discoveryDir.resolve("manifest.jsonl")).forEach(row -> {
            JsonNode change = changes.getOrDefault(location(row), MissingNode.getInstance());
            places.computeIfAbsent(row.path("blobSha").asString(), key -> new ArrayList<>()).add(
                    new CatalogueEntry.Place(row.path("repository").asString(), row.path("path").asString(),
                            row.path("scope").asString(), row.path("commitSha").asString(),
                            change.path("changedAt").stringValue(null), change.path("changedIn").stringValue(null)));
        });
        return places;
    }

    private static String location(JsonNode row) {
        return row.path("repository").asString() + "@" + row.path("commitSha").asString() + ":"
                + row.path("path").asString();
    }

    static CatalogueEntry.Image image(JsonNode row) {
        JsonNode facts = row.path("facts");
        JsonNode config = row.path("config");
        return new CatalogueEntry.Image(facts.path("finalUser").stringValue(null), facts.path("userKind").asString(),
                strings(facts.path("exposedPorts")), command(config.path("entrypoint")), command(config.path("cmd")),
                config.path("workdir").stringValue(null), strings(config.path("volumes")),
                config.path("healthcheck").stringValue(null), strings(config.path("shell")),
                config.path("stopSignal").stringValue(null), pairs(config.path("environment")),
                pairs(config.path("labels")));
    }

    static CatalogueEntry.Build build(JsonNode row) {
        JsonNode facts = row.path("facts");
        JsonNode stageRows = row.path("stages");
        List<CatalogueEntry.StageEntry> stages =
                IntStream.range(0, stageRows.size()).mapToObj(i -> stage(i, stageRows.get(i))).toList();
        List<CatalogueEntry.PackageEntry> packages = packages(facts.path("packageCalls"));
        return new CatalogueEntry.Build(stages, packages, strings(facts.path("buildKitFeatures")),
                row.path("config").path("layers").asInt(), facts.path("runs").asInt(),
                facts.path("opaqueRuns").asInt(), instructions(row.path("steps")),
                Runtimes.of(stages, packages, pairs(row.path("config").path("environment"))));
    }

    static CatalogueEntry.StageEntry stage(int index, JsonNode stage) {
        JsonNode base = stage.path("base");
        String digest = base.path("digest").stringValue(null);
        return new CatalogueEntry.StageEntry(index, stage.path("name").stringValue(null),
                base.path("name").stringValue(null), base.path("tag").stringValue(null), digest, digest != null,
                stage.path("fromStage").stringValue(null), strings(stage.path("copiesFrom")),
                stage.path("instructions").asInt(), stage.path("runs").asInt(), stage.path("commands").asInt(),
                stage.path("unreadRuns").asInt(), stage.path("layers").asInt(), copies(stage.path("copies")),
                permissions(stage.path("permissions")), strings(stage.path("unusual")));
    }

    static List<CatalogueEntry.Copy> copies(JsonNode copies) {
        List<CatalogueEntry.Copy> list = new ArrayList<>();
        copies.forEach(c -> list.add(new CatalogueEntry.Copy(c.path("instruction").asString(),
                strings(c.path("sources")), c.path("destination").stringValue(null), c.path("origin").asString(),
                c.path("from").stringValue(null), c.path("chown").stringValue(null),
                c.path("chmod").stringValue(null))));
        return list;
    }

    static List<CatalogueEntry.Permission> permissions(JsonNode changes) {
        List<CatalogueEntry.Permission> list = new ArrayList<>();
        changes.forEach(c -> list.add(new CatalogueEntry.Permission(c.path("command").asString(),
                c.path("setting").asString(), strings(c.path("paths")), c.path("recursive").asBoolean())));
        return list;
    }

    static List<CatalogueEntry.PackageEntry> packages(JsonNode calls) {
        List<CatalogueEntry.PackageEntry> packages = new ArrayList<>();
        calls.forEach(staged -> {
            JsonNode call = staged.path("call");
            String manager = call.path("manager").asString();
            call.path("packages").forEach(name -> packages.add(new CatalogueEntry.PackageEntry(
                    staged.path("stage").asInt(), manager, name.asString(), Versions.versioned(manager,
                    name.asString()), call.path("cleanedInSameRun").asBoolean())));
        });
        return packages;
    }

    static Map<String, Integer> instructions(JsonNode steps) {
        Map<String, Integer> counts = new TreeMap<>();
        steps.forEach(step -> counts.merge(step.path("keyword").asString(), 1, Integer::sum));
        return counts;
    }

    private static CatalogueEntry.Command command(JsonNode command) {
        return command.isMissingNode() || command.isNull() ? null
                : new CatalogueEntry.Command(command.path("form").asString(), strings(command.path("arguments")));
    }

    private static List<String> strings(JsonNode array) {
        List<String> strings = new ArrayList<>();
        array.forEach(value -> strings.add(value.asString()));
        return strings;
    }

    private static Map<String, String> pairs(JsonNode object) {
        Map<String, String> pairs = new LinkedHashMap<>();
        object.propertyNames().forEach(name -> pairs.put(name, object.path(name).asString()));
        return pairs;
    }
}
