package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class CorpusRoundTripTest {

    private static final Path CORPUS = Path.of("../testdata/corpus/dockerfiles");

    static Stream<String> cases() throws IOException {
        try (Stream<Path> dirs = Files.list(CORPUS)) {
            return dirs.filter(Files::isDirectory).map(dir -> String.valueOf(dir.getFileName())).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void eachRealCaseRoundTripsAsItsExpectationSays(String name) throws IOException {
        Path dir = CORPUS.resolve(name);
        String expected = new ObjectMapper().readTree(dir.resolve("expected.json").toFile()).path("roundTrip").asString();

        assertThat(CorpusOutcome.roundTrip(Files.readString(dir.resolve("Dockerfile"))))
                .as("corpus case " + name + ": roundTrip is not what expected.json says; if a change meant this,"
                        + " update expected.json")
                .isEqualTo(expected);
    }
}
