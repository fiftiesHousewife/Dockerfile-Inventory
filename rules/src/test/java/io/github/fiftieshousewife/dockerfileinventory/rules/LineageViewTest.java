package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LineageViewTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readJoinsEachEffectiveImageWithItsRootsNames(@TempDir Path lineage) throws IOException {
        Files.writeString(lineage.resolve("effective.jsonl"), """
                {"configDigest":"app","chain":[{"configDigest":"app","link":{"blobShas":["a"]}},\
                {"configDigest":"ubi","link":null}],"user":{"value":"1001","setBy":{"kind":"Unknown"}}}

                """);
        Files.writeString(lineage.resolve("images.jsonl"), """
                {"image":"ubi9/ubi","tag":"9.4","configDigest":"ubi"}
                """);

        assertThat(LineageView.read(lineage)).containsExactly(new ImageView("app", "1001", null,
                List.of(new ImageView.Group("app", List.of("a")), new ImageView.Group("ubi", List.of())),
                List.of("ubi9/ubi:9.4")));
    }

    @Test
    void namesGroupsImageTagsByConfigDigestSkippingImagesWithoutOne() {
        List<JsonNode> images = List.of(json("{\"image\":\"a\",\"tag\":\"1\",\"configDigest\":\"d\"}"),
                json("{\"image\":\"a\",\"tag\":\"2\",\"configDigest\":\"d\"}"),
                json("{\"image\":\"b\",\"tag\":\"1\",\"configDigest\":null}"),
                json("{\"image\":\"c\",\"tag\":\"1\"}"));

        assertThat(LineageView.names(images)).containsExactly(Map.entry("d", List.of("a:1", "a:2")));
    }

    @Test
    void viewOfAnImageWithAnEmptyChainHasNoRootNames() {
        ImageView view = LineageView.view(json("{\"configDigest\":\"x\",\"chain\":[],\"user\":{}}"),
                Map.of("x", List.of("x:1")));

        assertThat(view).satisfies(v -> {
            assertThat(v.chain()).isEmpty();
            assertThat(v.rootNames()).isEmpty();
            assertThat(v.user()).isNull();
        });
    }

    @Test
    void userSetByIsTheSettingImageOnlyWhenAnImageSetIt() {
        assertThat(LineageView.userSetBy(json("{\"kind\":\"Image\",\"configDigest\":\"b\",\"link\":"
                + "{\"blobShas\":[\"b-df\"]}}"))).isEqualTo(new ImageView.Group("b", List.of("b-df")));
        assertThat(LineageView.userSetBy(json("{\"kind\":\"Unknown\"}"))).isNull();
    }

    @Test
    void groupHasNoBlobsWhenTheImageHasNoLink() {
        assertThat(LineageView.group("d", json("{}").path("link"))).isEqualTo(new ImageView.Group("d", List.of()));
    }

    @Test
    void linesOfAMissingFileAreEmpty(@TempDir Path dir) {
        assertThat(LineageView.lines(dir.resolve("absent.jsonl"))).isEmpty();
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }
}
