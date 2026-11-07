package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.ImageRow;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LineageTreeTest {

    @Test
    void anImageWhoseParentIsNotInTheListIsARoot() {
        assertThat(LineageTree.render(List.of(new ImageRow("r/app:1", "outside:1", "NONE", List.of()),
                new ImageRow("r/child:1", "r/app:1", "NONE", List.of()))))
                .isEqualTo("<ul class=\"tree\"><li><code>r/app:1</code> · no Dockerfile<ul><li><code>r/child:1"
                        + "</code> · no Dockerfile</li></ul></li></ul>");
    }

    @Test
    void aNodeWithoutChildrenHasNoNestedList() {
        assertThat(LineageTree.node(new ImageRow("r/a:1", null, "NONE", List.of()), Map.of()))
                .isEqualTo("<li><code>r/a:1</code> · no Dockerfile</li>");
    }

    @Test
    void anImagesDockerfilesAreNamedWithItsTierFiveAtMost() {
        List<String> files = IntStream.range(0, 6).mapToObj(i -> "org/r: " + i).toList();

        assertThat(LineageTree.dockerfiles(new ImageRow("r/a:1", null, "EXACT", files.subList(0, 1))))
                .isEqualTo("exact: <code>org/r: 0</code>");
        assertThat(LineageTree.dockerfiles(new ImageRow("r/a:1", null, "PROBABLE", files)))
                .endsWith("<code>org/r: 4</code> and 1 more");
    }
}
