package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserKindTest {

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "-, ROOT", "'', ROOT", "root, ROOT", "0, ROOT", "0:0, ROOT", "1001, NUMERIC", "1001:0, NUMERIC",
        "app, NAMED", "app:root, NAMED", "${APP_UID}, UNRESOLVED",
    })
    void classifiesAnEffectiveUserAsDockerRunsIt(String user, UserKind kind) {
        assertThat(UserKind.of(user)).isEqualTo(kind);
    }
}
