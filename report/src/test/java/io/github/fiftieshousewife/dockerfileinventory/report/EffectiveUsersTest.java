package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EffectiveUsersTest {

    private static final Instant JANUARY = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant JUNE = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void eachDockerfileTakesTheUserOfItsNewestLinkedImage() {
        assertThat(EffectiveUsers.read(Path.of("src/test/resources/fixtures/lineage")))
                .containsExactly(Map.entry("blob-a", UserKind.NUMERIC));
    }

    @Test
    void anImagesLastPushIsItsLatestTagsAndUnpushedImagesAreLeftOut(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("images.jsonl"), """
                {"configDigest":"d","lastPushed":"2026-06-01T00:00:00Z"}
                {"configDigest":"d","lastPushed":"2026-01-01T00:00:00Z"}
                {"configDigest":"e","lastPushed":null}
                {"configDigest":null,"lastPushed":"2026-01-01T00:00:00Z"}
                """);

        assertThat(EffectiveUsers.pushed(dir.resolve("images.jsonl"))).isEqualTo(Map.of("d", JUNE));
    }

    @Test
    void usersAreReadByConfigDigestWithNullForNone(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("effective.jsonl"), """
                {"configDigest":"d","user":{"value":"1001","setBy":"x"}}
                {"configDigest":"e","user":{"value":null}}
                """);

        assertThat(EffectiveUsers.users(dir.resolve("effective.jsonl"))).satisfies(users -> {
            assertThat(users).containsEntry("d", "1001").containsKey("e");
            assertThat(users.get("e")).isNull();
        });
    }

    @Test
    void aBlobLinkedToSeveralImagesTakesTheNewestAndUnlinkedRowsAreIgnored(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("links.jsonl"), """
                {"configDigest":"old","tier":"EXACT","blobShas":["s"]}
                {"configDigest":"new","tier":"PROBABLE","blobShas":["s"]}
                {"configDigest":"none","tier":"NONE","blobShas":["t"]}
                """);

        assertThat(EffectiveUsers.newestImages(dir.resolve("links.jsonl"), Map.of("old", JANUARY, "new", JUNE)))
                .isEqualTo(Map.of("s", "new"));
    }

    @Test
    void theNewerOfTwoImagesWinsAndAnUnknownPushLoses() {
        Map<String, Instant> pushed = Map.of("a", JANUARY, "b", JUNE);

        assertThat(EffectiveUsers.newer("a", "b", pushed)).isEqualTo("b");
        assertThat(EffectiveUsers.newer("b", "a", pushed)).isEqualTo("b");
        assertThat(EffectiveUsers.newer("x", "a", pushed)).isEqualTo("a");
    }
}
