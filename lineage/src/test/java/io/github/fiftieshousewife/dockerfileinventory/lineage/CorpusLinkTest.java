package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CorpusLinkTest {

    private static final Path CORPUS = Path.of("../testdata/corpus/images");
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<String> cases() throws IOException {
        try (Stream<Path> dirs = Files.list(CORPUS)) {
            return dirs.filter(Files::isDirectory).map(dir -> String.valueOf(dir.getFileName())).sorted().toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void eachRealImageLinksAsItsExpectationSays(String name) throws IOException {
        Path dir = CORPUS.resolve(name);
        JsonNode expected = JSON.readTree(dir.resolve("expected.json").toFile());
        Resolved.Node image = new Resolved.Node(name,
                ImageConfig.parse(Files.readString(dir.resolve("config.json"))).orElseThrow());

        Link link = Links.of(List.of(image), Map.of(name, new Parent.Root()), dockerfiles(dir), Set.of()).get(name);

        List<String> blobShas = new ArrayList<>();
        expected.path("blobShas").forEach(sha -> blobShas.add(sha.asString()));

        assertThat(link).as("corpus image " + name + ": its link is not what expected.json says; if a change meant"
                + " this, update expected.json").satisfies(l -> {
                    assertThat(l.tier().name()).isEqualTo(expected.path("tier").asString());
                    assertThat(l.blobShas()).containsExactlyElementsOf(blobShas);
                });
    }

    static List<Dockerfile> dockerfiles(Path dir) throws IOException {
        List<Dockerfile> dockerfiles = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("dockerfiles.jsonl"))) {
            JsonNode row = JSON.readTree(line);
            List<Instruction> steps = new ArrayList<>();
            row.path("steps").forEach(s -> steps.add(new Instruction(s.path("keyword").asString(),
                    s.path("text").asString())));
            dockerfiles.add(new Dockerfile(row.path("blobSha").asString(), steps,
                    row.path("fromPath").stringValue(null), false, Set.of(row.path("repository").asString())));
        }
        return dockerfiles;
    }
}
