package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunBodyTest {

    @Test
    void aShellRunNeedsAtLeastOneCommandAndCopiesThem() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new RunBody.Shell(List.of())),
                () -> assertThat(new RunBody.Shell(new ArrayList<>(List.of("make"))).commands()).isUnmodifiable());
    }

    @Test
    void anExecRunNeedsAnArgumentAndAHeredocADelimiterAndBothCopyTheirLists() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new RunBody.Exec(List.of())),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new RunBody.Heredoc("", " ", true,
                        List.of("make"))),
                () -> assertThat(new RunBody.Exec(new ArrayList<>(List.of("make"))).arguments()).isUnmodifiable(),
                () -> assertThat(new RunBody.Heredoc("", "EOF", false, new ArrayList<>(List.of("make"))).lines())
                        .isUnmodifiable());
    }

    @Test
    void aHeredocIsItsLinesAsAQuotedScriptUnderEofUnlessSaidOtherwise() {
        RunBody.Heredoc heredoc = RunBody.Heredoc.of("set -e", "make");

        assertAll(
                () -> assertThat(heredoc).isEqualTo(new RunBody.Heredoc("", "EOF", true, List.of("set -e", "make"))),
                () -> assertThat(heredoc.command("bash").command()).isEqualTo("bash"),
                () -> assertThat(heredoc.delimitedBy("END").delimiter()).isEqualTo("END"),
                () -> assertThat(heredoc.expanding().quoted()).isFalse());
    }
}
