package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Every Dockerfile in the estate, with its facts and locations, read from the earlier stages' files. */
public record Estate(List<Dockerfile> dockerfiles) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public Estate {
        dockerfiles = List.copyOf(dockerfiles);
    }

    public static Estate read(Path factsDir, Path discoveryDir, Path lineageDir) {
        return new Estate(lines(factsDir.resolve("facts.jsonl")).stream().map(Estate::dockerfile).toList());
    }

    private static Dockerfile dockerfile(JsonNode row) {
        JsonNode facts = row.path("facts");
        JsonNode base = facts.path("base");
        int finalStage = facts.path("stageCount").asInt() - 1;
        Set<String> packages = new TreeSet<>();
        Set<String> managers = new TreeSet<>();
        facts.path("packageCalls").forEach(staged -> {
            JsonNode call = staged.path("call");
            managers.add(call.path("manager").asString());
            if (staged.path("stage").asInt() == finalStage) {
                call.path("packages").forEach(p -> packages.add(p.asString()));
            }
        });
        return new Dockerfile(row.path("blobSha").asString(), row.path("textHash").asString(),
                row.path("signatureHash").asString(), row.path("signature").asString(),
                new Base(base.path("name").stringValue(null), base.path("tag").stringValue(null),
                        base.path("digest").stringValue(null)),
                packages, managers, facts.path("userKind").asString(), facts.path("runs").asInt(),
                facts.path("opaqueRuns").asInt(), List.of());
    }

    static List<JsonNode> lines(Path file) {
        try {
            return Files.exists(file)
                    ? Files.readAllLines(file).stream().filter(l -> !l.isBlank()).map(JSON::readTree).toList()
                    : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
