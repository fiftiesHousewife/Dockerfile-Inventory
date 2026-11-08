package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfile.model.RunBody;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordCallsTest {

    private static final String DSL = "io.github.fiftieshousewife.dockerfile.model.";

    @Test
    void aHeredocSaysOnlyWhatDiffersFromEofAndQuoted() {
        assertAll(
                () -> assertThat(RecordCalls.heredocParts("EOF", true).toString()).isEmpty(),
                () -> assertThat(RecordCalls.heredocParts("END", false).toString())
                        .isEqualTo(".delimitedBy(\"END\").expanding()"));
    }

    @Test
    void aShellBodyIsItsCommandsAsAList() {
        assertThat(RecordCalls.body(new RunBody.Shell(List.of("make"))).toString())
                .isEqualTo("new " + DSL + "RunBody.Shell(java.util.List.of(\"make\"))");
    }
}
