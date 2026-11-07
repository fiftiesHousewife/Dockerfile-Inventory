package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConvertCopyTest {

    @Test
    void aCopyWithFromAndChownIsTypedWithTheLastValueOfARepeatedFlag() {
        assertThat(ConvertCopy.copy("COPY --from=a --chown=1:0 --from=b x y /dst/"))
                .isEqualTo(new Instruction.Copy(List.of("x", "y"), "/dst/", "1:0", "b"));
    }

    @Test
    void aCopyContinuedOverLinesIsReadAsOneLine() {
        assertThat(ConvertCopy.copy("COPY a \\\n    /dst/")).isEqualTo(new Instruction.Copy(List.of("a"), "/dst/",
                null, null));
    }

    @Test
    void whatTheModelDoesNotTypeStaysRawWithItsReason() {
        assertThat(List.of("COPY <<EOF /etc/x\nhello\nEOF", "COPY --link a /b", "COPY [\"a\", \"/b\"]", "COPY a"))
                .map(text -> ((Instruction.Raw) ConvertCopy.copy(text)).reason())
                .containsExactly("COPY with a heredoc", "COPY with flag --link", "COPY in JSON form",
                        "COPY without a source and a destination");
    }

    @Test
    void wordsAreTheInstructionsWordsAfterItsKeyword() {
        assertThat(ConvertCopy.words("COPY --from=a x /y")).containsExactly("--from=a", "x", "/y");
    }

    @Test
    void anUnknownFlagIsTheFirstThatIsNeitherFromNorChown() {
        assertThat(ConvertCopy.unknownFlag(List.of("--from=a", "--chmod=7", "--link"))).contains("--chmod=7");
        assertThat(ConvertCopy.unknownFlag(List.of("--from=a", "--chown=1"))).isEmpty();
    }

    @Test
    void aFlagsValueIsTheLastGivenOrNullWhenAbsent() {
        assertThat(ConvertCopy.flag(List.of("--from=a", "--from=b"), "--from=")).isEqualTo("b");
        assertThat(ConvertCopy.flag(List.of("--from=a"), "--chown=")).isNull();
    }
}
