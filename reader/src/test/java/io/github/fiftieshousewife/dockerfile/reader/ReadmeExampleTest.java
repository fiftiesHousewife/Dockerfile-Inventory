package io.github.fiftieshousewife.dockerfile.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReadmeExampleTest {

    @Test
    void theExampleReadsADockerfileIntoTheModel() {
        Conversion conversion = DockerfileReader.read("FROM ubi9\nRUN dnf install -y git && dnf clean all\nUSER 1001\n");
        Dockerfile model = conversion.model();
        List<Raw> untyped = conversion.raw();

        assertAll(
                () -> assertThat(model.stages().getFirst().instructions()).hasSize(2),
                () -> assertThat(untyped).isEmpty());
    }

    @Test
    void theReadmeShowsThisExampleLineForLine() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        int start = readme.indexOf("```java\n") + "```java\n".length();
        String example = readme.substring(start, readme.indexOf("\n```", start));
        String test = Files.readString(Path.of("src/test/java/io/github/fiftieshousewife/dockerfile/reader/ReadmeExampleTest.java"));

        assertThat(example.lines().map(String::strip).filter(line -> !line.isEmpty()))
                .allMatch(test::contains);
    }
}
