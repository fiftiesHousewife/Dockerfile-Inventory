package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContinuationsTest {

    @Test
    void removedDropsEachBackslashNewlineAsDockerDoesTrailingSpacesAndCarriageReturnsIncluded() {
        assertThat(Continuations.removed("a=1 \\\n    b=2 \\  \r\n  c=3")).isEqualTo("a=1     b=2   c=3");
    }

    @Test
    void oneLineJoinsDropsCommentLinesCollapsesWhitespaceAndKeepsABackslashEndingTheLastLine() {
        assertThat(Continuations.oneLine("COPY a \\\n  # why\n\tC:\\\\Windows\\\\ \n"))
                .isEqualTo("COPY a C:\\\\Windows\\\\");
        assertThat(Continuations.oneLine("RUN make \\  \n    && make install")).isEqualTo("RUN make && make install");
    }
}
