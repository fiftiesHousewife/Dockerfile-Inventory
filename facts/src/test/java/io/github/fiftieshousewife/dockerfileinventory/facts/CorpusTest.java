package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CorpusTest {

    private static final Path CORPUS = Path.of("../testdata/corpus/dockerfiles");
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<String> cases() throws IOException {
        try (Stream<Path> dirs = Files.list(CORPUS)) {
            return dirs.filter(Files::isDirectory).map(dir -> String.valueOf(dir.getFileName())).sorted().toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void eachRealCaseParsesAsItsExpectationSays(String name) throws IOException {
        Path dir = CORPUS.resolve(name);
        JsonNode expected = JSON.readTree(dir.resolve("expected.json").toFile());
        ParseResult result = new FactsParser().parse(name, Files.readString(dir.resolve("Dockerfile")));

        assertThat(Corpus.outcome(result)).as(Corpus.ratchet(name, "parse")).isEqualTo(expected.path("parse")
                .asString());
        if (result instanceof ParseResult.Parsed parsed) {
            assertThat(FactExtractor.extract(parsed.file()).opaqueRuns()).as(Corpus.ratchet(name, "unreadableRuns"))
                    .isEqualTo(expected.path("unreadableRuns").asInt());
        }
    }
}
