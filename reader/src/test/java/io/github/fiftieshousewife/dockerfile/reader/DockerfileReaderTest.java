package io.github.fiftieshousewife.dockerfile.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfile.model.Copy;
import io.github.fiftieshousewife.dockerfile.model.User;
import io.github.fiftieshousewife.dockerfile.parser.DockerParser;
import io.github.fiftieshousewife.dockerfile.parser.DockerParsingException;
import org.junit.jupiter.api.Test;

class DockerfileReaderTest {

    private static final String MULTI_STAGE = "FROM ubi9 AS build\nRUN make\nFROM ubi9-minimal\n"
            + "COPY --from=build /out /app\nUSER 1001\n";

    @Test
    void readingTextGivesTheModelWithEachStageAndItsInstructionsTyped() {
        Conversion conversion = DockerfileReader.read(MULTI_STAGE);

        assertAll(
                () -> assertThat(conversion.model().stages()).hasSize(2),
                () -> assertThat(conversion.model().stages().getLast().instructions())
                        .hasExactlyElementsOfTypes(Copy.class, User.class));
    }

    @Test
    void readingAParsedTreeGivesTheSameModelAsReadingItsText() {
        assertThat(DockerfileReader.read(DockerParser.parseText(MULTI_STAGE)).model())
                .isEqualTo(DockerfileReader.read(MULTI_STAGE).model());
    }

    @Test
    void textThatDoesNotParseThrowsTheSyntaxErrorWithItsLine() {
        assertThatThrownBy(() -> DockerfileReader.read("FROM ubi9\nRUNN make\n"))
                .isInstanceOfSatisfying(DockerParsingException.class, e -> assertThat(e.getLine()).isEqualTo(2));
    }
}
