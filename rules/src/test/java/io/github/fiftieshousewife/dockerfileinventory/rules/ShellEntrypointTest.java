package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class ShellEntrypointTest {

    @Test
    void h2ReportsAShellFormFinalEntrypoint() {
        Rule h2 = new ShellEntrypoint();

        assertAll(
                () -> assertThat(h2.check(Subjects.of("FROM ubi9\nENTRYPOINT java -jar app.jar\n"))).hasSize(1),
                () -> assertThat(h2.check(Subjects.of("FROM ubi9\nENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n")))
                        .isEmpty(),
                () -> assertThat(h2.check(Subjects.of("FROM ubi9\n"))).isEmpty());
    }
}
