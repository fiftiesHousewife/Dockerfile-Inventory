package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConvertCopyTest {

    @Test
    void aCopyWithFromAndChownIsTypedWithTheLastValueOfARepeatedFlag() {
        assertThat(ConvertCopy.copy("COPY --from=a --chown=1:0 --from=b x y /dst/"))
                .isEqualTo(new Instruction.Copy(List.of("x", "y"), "/dst/", "1:0", "b"));
    }

    @Test
    void aCopyKeepsItsModeAndLink() {
        assertThat(ConvertCopy.copy("COPY --chown=1001 --chmod=755 --link run.sh /bin/"))
                .isEqualTo(new Instruction.Copy(List.of("run.sh"), "/bin/", "1001", null, "755", true, false));
    }

    @Test
    void aCopyInJsonFormIsTypedWithItsPathsDecoded() {
        assertThat(ConvertCopy.copy("COPY --chmod=644 [\"with space\", \"/opt/\"]"))
                .isEqualTo(new Instruction.Copy(List.of("with space"), "/opt/", null, null, "644", false, true));
    }

    @Test
    void aPlainHeredocIsTypedWithItsLinesAndWhetherItsDelimiterIsQuoted() {
        assertThat(List.of("COPY <<'EOF' /etc/x.conf\n[engine]\na = 1\nEOF", "COPY <<EOF /etc/y.json\n{}\nEOF\n"))
                .map(ConvertCopy::copy)
                .containsExactly(new Instruction.CopyHeredoc("/etc/x.conf", "EOF", true, List.of("[engine]", "a = 1")),
                        new Instruction.CopyHeredoc("/etc/y.json", "EOF", false, List.of("{}")));
    }

    @Test
    void aCopyContinuedOverLinesIsReadAsOneLine() {
        assertThat(ConvertCopy.copy("COPY a \\\n    /dst/")).isEqualTo(new Instruction.Copy(List.of("a"), "/dst/",
                null, null));
    }

    @Test
    void whatTheModelDoesNotTypeStaysRawWithItsReason() {
        assertThat(List.of("COPY --chmod=644 <<EOF /etc/x\nhello\nEOF", "COPY --exclude=*.md a /b", "COPY [\"a\", /b]",
                "COPY a", "COPY [\"/b\"]"))
                .map(text -> ((Instruction.Raw) ConvertCopy.copy(text)).reason())
                .containsExactly("COPY with flags, several heredocs or <<-", "COPY with flag --exclude", "COPY in JSON form that is not JSON",
                        "COPY without a source and a destination", "COPY without a source and a destination");
    }

    @Test
    void aHeredocIsPlainOnlyAloneWithoutFlagsOrADashAndClosedByItsDelimiter() {
        assertThat(List.of("COPY <<\"END\" /a\nx\nEND", "COPY <<-EOF /a\n\tx\nEOF", "COPY <<A <<B /d/\na\nA\nb\nB",
                "COPY <<EOF /a\nx"))
                .map(ConvertCopy::heredoc)
                .containsExactly(Optional.of(new Instruction.CopyHeredoc("/a", "END", true, List.of("x"))),
                        Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void wordsAreTheInstructionsWordsAfterItsKeyword() {
        assertThat(ConvertCopy.words("COPY --from=a x /y")).containsExactly("--from=a", "x", "/y");
    }

    @Test
    void anUnknownFlagIsTheFirstThatIsNotFromChownChmodOrABareLink() {
        assertThat(ConvertCopy.unknownFlag(List.of("--from=a", "--chmod=7", "--link", "--link=false")))
                .contains("--link=false");
        assertThat(ConvertCopy.unknownFlag(List.of("--from=a", "--chown=1", "--chmod=7", "--link"))).isEmpty();
    }

    @Test
    void pathsAreTheWordsOrTheJsonArrayDecodedOrNothingWhenTheJsonDoesNotRead() {
        assertThat(List.of(List.of("a", "/b"), List.of("[\"a", "b\",", "\"/c\"]"), List.of("[\"a\",", "/b]")))
                .map(ConvertCopy::paths)
                .containsExactly(Optional.of(List.of("a", "/b")), Optional.of(List.of("a b", "/c")), Optional.empty());
    }

    @Test
    void aFlagsValueIsTheLastGivenOrNullWhenAbsent() {
        assertThat(ConvertCopy.flag(List.of("--from=a", "--from=b"), "--from=")).isEqualTo("b");
        assertThat(ConvertCopy.flag(List.of("--from=a"), "--chown=")).isNull();
    }
}
