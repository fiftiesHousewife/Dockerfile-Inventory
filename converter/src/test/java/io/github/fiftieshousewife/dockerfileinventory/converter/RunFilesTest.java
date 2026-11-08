package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class RunFilesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path discovery;

    @TempDir
    Path out;

    @Test
    void locationsGroupTheManifestsRowsByBlobSkippingBlankLines() {
        String manifest = """
                {"repository":"a/one","path":"Dockerfile","blobSha":"x"}

                {"repository":"b/two","path":"build/Dockerfile","blobSha":"x"}
                {"repository":"c/three","path":"Dockerfile","blobSha":"y"}
                """;

        assertThat(RunFiles.locations(manifest, JSON)).containsOnlyKeys("x", "y").satisfies(
                l -> assertThat(l.get("x")).containsExactly(new Location("a/one", "Dockerfile"),
                        new Location("b/two", "build/Dockerfile")),
                l -> assertThat(l.get("y")).containsExactly(new Location("c/three", "Dockerfile")));
    }

    @Test
    void withoutAManifestThereAreNoLocations() {
        assertThat(new RunFiles(discovery, out).locations(JSON)).isEmpty();
    }

    @Test
    void blobsAreTheFinishedFilesInOrderWithoutPartialDownloadsOrFolders() throws IOException {
        Files.createDirectories(discovery.resolve("blobs/folder"));
        Files.writeString(discovery.resolve("blobs/b"), "FROM b");
        Files.writeString(discovery.resolve("blobs/a"), "FROM a");
        Files.writeString(discovery.resolve("blobs/c.part"), "FROM");

        assertThat(new RunFiles(discovery, out).blobs()).extracting(blob -> String.valueOf(blob.getFileName()))
                .containsExactly("a", "b");
    }

    @Test
    void writeCreatesTheFoldersItNeeds() {
        RunFiles files = new RunFiles(discovery, out);

        files.write("top.json", "{}");
        files.write("generated/images/x/A.java", "class A {}");

        assertThat(List.of(RunFiles.read(out.resolve("top.json")),
                RunFiles.read(out.resolve("generated/images/x/A.java")))).containsExactly("{}", "class A {}");
    }

    @Test
    void aFileIsWrittenWithTheDirectoriesItNeeds(@TempDir Path root) {
        RunFiles.writeFile(root.resolve("a/b/c.txt"), "text");

        assertThat(root.resolve("a/b/c.txt")).hasContent("text");
    }
}
