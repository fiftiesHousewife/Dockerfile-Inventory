package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReadmeExampleTest {

    @Test
    void theExampleBuildsAnImageThatMeetsThePolicyAndRendersIt() {
        BaseImage base = new BaseImage("registry.access.redhat.com/ubi9/ubi-minimal", "9.6",
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        Built built = ImageBuilder.finalStage(base)
                .install("python3")
                .copy("app/", "/opt/app/")
                .user(1001)
                .entrypoint("python3", "/opt/app/main.py")
                .build(Policy.of(List.of(new Policy.Approved("ubi9/ubi-minimal", "9"))));
        String dockerfile = DockerfileRenderer.render(built.dockerfile(), Target.DEFAULT);

        assertThat(dockerfile).startsWith("FROM registry.access.redhat.com/ubi9/ubi-minimal:9.6@sha256:");
    }

    @Test
    void theReadmeShowsThisExampleLineForLine() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        int start = readme.indexOf("```java\n") + "```java\n".length();
        String example = readme.substring(start, readme.indexOf("\n```", start));
        String test = Files.readString(Path.of("src/test/java/io/github/fiftieshousewife/dockerfile/model/ReadmeExampleTest.java"));

        assertThat(example.lines().map(String::strip).filter(line -> !line.isEmpty()))
                .allMatch(test::contains);
    }
}
