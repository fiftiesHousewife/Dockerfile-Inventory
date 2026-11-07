package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.BaseUse;
import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.ImageRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineageViewTest {

    @Test
    void basesCountChildrenMostFirstThenByNameLeavingOutRootsAndAmbiguousParents() {
        List<ImageRow> images = List.of(
                new ImageRow("a", "z", "NONE", List.of()),
                new ImageRow("b", "y", "NONE", List.of()),
                new ImageRow("c", "y", "NONE", List.of()),
                new ImageRow("d", "x", "NONE", List.of()),
                new ImageRow("e", null, "NONE", List.of()),
                new ImageRow("f", "ambiguous: x, y", "NONE", List.of()));

        assertThat(LineageView.bases(images))
                .containsExactly(new BaseUse("y", 2), new BaseUse("x", 1), new BaseUse("z", 1));
    }
}
