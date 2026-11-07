package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BasesSectionTest {

    @Test
    void theTableShowsTheTopTwentyBasesWithAnEmptyCellForNoTag() {
        List<BaseRow> bases = IntStream.range(0, 21)
                .mapToObj(i -> new BaseRow("b" + i, i == 0 ? null : "1", "b", 21 - i, 0, "unknown", null)).toList();

        assertThat(BasesSection.table(bases))
                .contains("<tr><td class=\"code\">b0</td><td class=\"code\"></td><td class=\"num\">21</td>")
                .contains(">b19<").doesNotContain(">b20<");
    }

    @Test
    void theNoMatchNoteAppearsOnlyWhenThereAreBasesAndNoneMatchedTheList() {
        BaseRow unknown = new BaseRow("a", null, "a", 1, 0, "unknown", null);
        BaseRow eol = new BaseRow("b", null, "b", 1, 0, "eol", null);

        assertThat(BasesSection.noEolMatch(List.of(unknown))).contains("No base matched an end-of-life list");
        assertThat(BasesSection.noEolMatch(List.of(unknown, eol))).isEmpty();
        assertThat(BasesSection.noEolMatch(List.of())).isEmpty();
    }
}
