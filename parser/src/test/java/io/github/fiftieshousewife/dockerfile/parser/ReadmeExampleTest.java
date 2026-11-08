package io.github.fiftieshousewife.dockerfile.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReadmeExampleTest {

    @Test
    void theExampleParsesAndPrintsBackTheSameText() {
        Docker.File dockerfile = DockerParser.parseText("FROM ubi9\nUSER 1001\n");
        String printed = dockerfile.printAll();

        assertThat(printed).isEqualTo("FROM ubi9\nUSER 1001\n");
    }

    @Test
    void theReadmeShowsThisExampleLineForLine() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        int start = readme.indexOf("```java\n") + "```java\n".length();
        String example = readme.substring(start, readme.indexOf("\n```", start));
        String test = Files.readString(Path.of("src/test/java/io/github/fiftieshousewife/dockerfile/parser/ReadmeExampleTest.java"));

        assertThat(example.lines().map(String::strip).filter(line -> !line.isEmpty()))
                .allMatch(test::contains);
    }
}
