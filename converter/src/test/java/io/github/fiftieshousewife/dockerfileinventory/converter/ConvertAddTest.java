package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.Instruction;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConvertAddTest {

    @Test
    void anAddIsTypedWithItsFlags() {
        assertThat(ConvertAdd.add("ADD --chown=1001:0 --chmod=644 --checksum=sha256:ab --keep-git-dir --link "
                + "https://example.com/a.tgz /opt/"))
                .isEqualTo(new Instruction.Add(List.of("https://example.com/a.tgz"), "/opt/", "1001:0", "644",
                        "sha256:ab", true, true, false));
    }

    @Test
    void aLocalFileOrJsonFormIsTypedAsWritten() {
        assertThat(List.of("ADD app.jar run.sh /opt/", "ADD [\"with space.tar\", \"/opt/\"]"))
                .map(ConvertAdd::add)
                .containsExactly(new Instruction.Add(List.of("app.jar", "run.sh"), "/opt/", null, null, null, false,
                                false, false),
                        new Instruction.Add(List.of("with space.tar"), "/opt/", null, null, null, false, false, true));
    }

    @Test
    void whatTheModelDoesNotTypeStaysRawWithItsReason() {
        assertThat(List.of("ADD <<EOF /x\nhi\nEOF", "ADD --exclude=*.md . /src/", "ADD [\"a\", /b]", "ADD a"))
                .map(text -> ((Instruction.Raw) ConvertAdd.add(text)).reason())
                .containsExactly("ADD with a heredoc", "ADD with flag --exclude", "ADD in JSON form that is not JSON",
                        "ADD without a source and a destination");
    }
}
