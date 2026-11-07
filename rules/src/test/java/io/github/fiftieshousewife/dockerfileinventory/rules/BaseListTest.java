package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseListTest {

    @Test
    void readSkipsTheHeaderAndKeepsEveryEntry() {
        BaseList list = BaseList.read(Path.of("src/test/resources/fixtures/eol.csv"));

        assertThat(list.lookup("ubi9/ubi-minimal", "9.2-1100"))
                .contains(new BaseList.Entry("ubi9/ubi-minimal", "9.2", "eol", "2025-11-30"));
    }

    @Test
    void entryReadsTwoOrFourFieldsLeavingMissingOnesEmptyOrNull() {
        assertThat(BaseList.entry(" ubi9/ubi , 9 ")).isEqualTo(new BaseList.Entry("ubi9/ubi", "9", null, null));
        assertThat(BaseList.entry("alpine")).isEqualTo(new BaseList.Entry("alpine", "", null, null));
        assertThat(BaseList.entry("ubi9/ubi,9.2,eol,2025-11-30"))
                .isEqualTo(new BaseList.Entry("ubi9/ubi", "9.2", "eol", "2025-11-30"));
    }

    @Test
    void lookupTakesTheLongestCoveringPrefixForThePath() {
        BaseList list = new BaseList(List.of(new BaseList.Entry("ubi", "", null, null),
                new BaseList.Entry("ubi", "9", null, null), new BaseList.Entry("ubi", "9.2", null, null)));

        assertThat(list.lookup("ubi", "9.2.1")).hasValueSatisfying(e -> assertThat(e.tagPrefix()).isEqualTo("9.2"));
        assertThat(list.lookup("ubi", "9.4")).hasValueSatisfying(e -> assertThat(e.tagPrefix()).isEqualTo("9"));
        assertThat(list.lookup("ubi", null)).hasValueSatisfying(e -> assertThat(e.tagPrefix()).isEmpty());
        assertThat(list.lookup("alpine", "3")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"9, 9, true", "9, 9.4, true", "9, 9-1100, true", "9, 91, false", "'', anything, true",
        "9.2, 9.20, false"})
    void coversAPrefixEndingOnAVersionBoundary(String prefix, String tag, boolean covers) {
        assertThat(BaseList.covers(prefix, tag)).isEqualTo(covers);
    }
}
