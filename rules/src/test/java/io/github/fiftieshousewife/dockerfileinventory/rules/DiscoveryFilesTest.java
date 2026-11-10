package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryFilesTest {

    @TempDir
    Path dir;

    @BeforeEach
    void blobs() throws IOException {
        Files.createDirectories(dir.resolve("blobs/sub"));
        Files.writeString(dir.resolve("blobs/b"), "FROM ubi9\n");
        Files.writeString(dir.resolve("blobs/a"), "not a dockerfile\n");
        Files.writeString(dir.resolve("blobs/c.part"), "FROM ubi9\n");
    }

    @Test
    void blobsAreTheStoredFilesInOrderWithoutPartFilesOrDirectories() {
        assertThat(new DiscoveryFiles(dir).blobs()).extracting(p -> String.valueOf(p.getFileName()))
                .containsExactly("a", "b");
    }

    @Test
    void subjectsParseEachBlobOnceKeyedByItsSha() {
        assertThat(new DiscoveryFiles(dir).subjects()).extracting(Subject::blobSha, Subject::isParsed)
                .containsExactly(tuple("a", false), tuple("b", true));
    }

    @Test
    void subjectCarriesTheFactsOfAParsedFileOrTheParsersMessage() {
        FactsParser parser = new FactsParser();

        assertAll(
                () -> assertThat(DiscoveryFiles.subject(parser, "x", "FROM ubi9:9.4\n").facts().base().tag())
                        .isEqualTo("9.4"),
                () -> assertThat(DiscoveryFiles.subject(parser, "x", "not a dockerfile\n").failure())
                        .startsWith("Syntax error"));
    }

    @Test
    void textIsABlobsContentOrNullWhenItWasNotFetched() {
        DiscoveryFiles files = new DiscoveryFiles(dir);

        assertAll(
                () -> assertThat(files.text("b")).isEqualTo("FROM ubi9\n"),
                () -> assertThat(files.text("missing")).isNull());
    }

    @Test
    void locationsListEveryManifestRowUnderItsBlob() throws IOException {
        Files.writeString(dir.resolve("manifest.jsonl"), """
                {"repository":"org/one","path":"Dockerfile","blobSha":"b"}

                {"repository":"org/two","path":"app/Dockerfile","blobSha":"b"}
                """);

        assertThat(new DiscoveryFiles(dir).locations()).containsExactly(Map.entry("b",
                List.of(new Location("org/one", "Dockerfile"), new Location("org/two", "app/Dockerfile"))));
    }

    @Test
    void locationsAreEmptyWithoutAManifest() {
        assertThat(new DiscoveryFiles(dir).locations()).isEmpty();
    }
}
