package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionPartsTest {

    @Test
    void aHeredocsDelimiterIsSingleQuotedWhenQuotedAndAnEmptyBodyHasNoLines() {
        assertThat(List.of(new Instruction.CopyHeredoc("/a", "EOF", true, List.of()),
                new Instruction.CopyHeredoc("/a", "EOF", false, List.of("x"))))
                .map(InstructionParts::heredoc).containsExactly("COPY <<'EOF' /a\nEOF", "COPY <<EOF /a\nx\nEOF");
    }

    @Test
    void aRunBodyIsItsJoinedCommandsItsJsonArrayOrItsHeredoc() {
        assertThat(List.of(new RunBody.Shell(List.of("a", "b")), new RunBody.Exec(List.of("a", "b")),
                new RunBody.Heredoc("", "EOF", true, List.of("a"))))
                .map(InstructionParts::body)
                .containsExactly("a \\\n    && b", "[\"a\", \"b\"]", "<<'EOF'\na\nEOF");
        assertThat(InstructionParts.body(new RunBody.Heredoc("cat > /etc/x", "EOF", true, List.of("a"))))
                .isEqualTo("cat > /etc/x <<'EOF'\na\nEOF");
    }

    @Test
    void aHeredocPutsWhatFollowsTheMarkerOnItsFirstLine() {
        assertThat(List.of(InstructionParts.heredoc("EOF", false, "", List.of("x")),
                InstructionParts.heredoc("END", true, "/etc/a", List.of())))
                .containsExactly("<<EOF\nx\nEOF", "<<'END' /etc/a\nEND");
    }

    @Test
    void aCopysPathsEndWithTheDestinationInItsForm() {
        assertThat(List.of(InstructionParts.paths(List.of("a", "b"), "/c/", false),
                InstructionParts.paths(List.of("a b"), "/c/", true)))
                .containsExactly("a b /c/", "[\"a b\", \"/c/\"]");
    }

    @Test
    void copyWritesFromBeforeChownAndLeavesOutWhatIsNotSet() {
        assertThat(InstructionParts.copy(new Instruction.Copy(List.of("a", "b"), "/d/", null, null)))
                .isEqualTo("COPY a b /d/");
        assertThat(InstructionParts.copy(new Instruction.Copy(List.of("a"), "/d/", "1:0", "build")))
                .isEqualTo("COPY --from=build --chown=1:0 a /d/");
    }

    @Test
    void aCommandIsAJsonArrayInExecFormAndItsOneStringInShellForm() {
        assertThat(List.of(InstructionParts.command(List.of("curl", "-f"), CommandForm.EXEC),
                InstructionParts.command(List.of("curl -f || exit 1"), CommandForm.SHELL)))
                .containsExactly("[\"curl\", \"-f\"]", "curl -f || exit 1");
    }
}
