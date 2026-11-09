package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CombineTest {

    @TempDir
    Path dir;

    private Path source(String name, String repository, String blob) throws IOException {
        Path source = Files.createDirectories(dir.resolve(name));
        Files.createDirectories(source.resolve("blobs"));
        Files.writeString(source.resolve("manifest.jsonl"), "{\"repository\":\"" + repository + "\",\"blobSha\":\""
                + blob + "\"}\n{\"repository\":\"shared/base\",\"blobSha\":\"base\"}\n");
        Files.writeString(source.resolve("repos.jsonl"), "{\"repository\":\"" + repository + "\"}\n");
        Files.writeString(source.resolve("changes.jsonl"), "{\"repository\":\"" + repository + "\",\"path\":\"D\"}\n");
        Files.writeString(source.resolve("blobs").resolve(blob), "FROM " + blob + "\n");
        Files.writeString(source.resolve("blobs").resolve("base"), "FROM ubi9\n");
        Files.writeString(source.resolve("blobs").resolve("half.part"), "FROM");
        return source;
    }

    @Test
    void everyFileIsMergedEachLineOnceAndEachBlobOnce() throws IOException {
        Path out = dir.resolve("out");

        Combine.Summary summary = Combine.of(List.of(source("a", "acme/api", "aaa"), source("b", "beta/web", "bbb")),
                out);

        assertAll(
                () -> assertThat(Files.readAllLines(out.resolve("manifest.jsonl"))).hasSize(3),
                () -> assertThat(Files.readAllLines(out.resolve("repos.jsonl"))).hasSize(2),
                () -> assertThat(Files.readAllLines(out.resolve("changes.jsonl"))).hasSize(2),
                () -> assertThat(out.resolve("blobs")).isDirectoryContaining(p -> p.endsWith("aaa"))
                        .isDirectoryContaining(p -> p.endsWith("base"))
                        .isDirectoryNotContaining(p -> p.toString().endsWith(".part")),
                () -> assertThat(summary).isEqualTo(new Combine.Summary(3, 2, 3, List.of())));
    }

    @Test
    void aRepositoryInMoreThanOneSourceIsReported() throws IOException {
        Combine.Summary summary = Combine.of(List.of(source("a", "acme/api", "aaa"), source("b", "acme/api", "bbb")),
                dir.resolve("out"));

        assertThat(summary.repeated()).containsExactly("acme/api");
    }

    @Test
    void aFolderThatIsNoDiscoveryOutputIsRefusedByName() throws IOException {
        Path empty = Files.createDirectories(dir.resolve("empty"));

        assertThatIllegalArgumentException().isThrownBy(() -> Combine.of(List.of(empty), dir.resolve("out")))
                .withMessage(empty + " is not a discovery output (no manifest.jsonl, repos.jsonl and blobs/)");
    }

    @Test
    void theYamlTheSourcesFoundIsMergedTooEachFileOnce() throws IOException {
        Path a = source("a", "acme/api", "aaa");
        Path b = source("b", "beta/web", "bbb");
        for (Path source : List.of(a, b)) {
            Files.createDirectories(source.resolve("yaml"));
            Files.writeString(source.resolve("yaml/y1"), "kind: Pod\n");
            Files.writeString(source.resolve("yaml.jsonl"), "{\"repository\":\"" + source.getFileName()
                    + "\",\"blobSha\":\"y1\"}\n");
        }
        Path out = dir.resolve("out");

        Combine.of(List.of(a, b), out);

        assertAll(
                () -> assertThat(Files.readAllLines(out.resolve("yaml.jsonl"))).hasSize(2),
                () -> assertThat(out.resolve("yaml/y1")).hasContent("kind: Pod\n"));
    }
}
