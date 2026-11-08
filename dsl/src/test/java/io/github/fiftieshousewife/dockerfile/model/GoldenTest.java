package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GoldenTest {

    static final Path GOLDEN = Path.of("src/test/resources/golden");

    static Stream<Arguments> goldens() {
        return Goldens.all().keySet().stream().sorted().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("goldens")
    void eachGoldenRendersExactlyItsCommittedFile(String name) throws IOException {
        String rendered = DockerfileRenderer.render(Goldens.all().get(name).build(Goldens.POLICY).dockerfile(),
                Target.DEFAULT);

        assertThat(rendered).isEqualTo(Files.readString(GOLDEN.resolve(name + ".Dockerfile")));
    }

    @Test
    void theReadmeHasOneLineForEachGoldenAndNoOther() throws IOException {
        List<String> described = Files.readAllLines(GOLDEN.resolve("README.md")).stream()
                .filter(line -> line.startsWith("| `"))
                .map(line -> line.substring(3, line.indexOf('`', 3)))
                .toList();

        assertThat(described).containsExactlyInAnyOrderElementsOf(Goldens.all().keySet());
    }
}
