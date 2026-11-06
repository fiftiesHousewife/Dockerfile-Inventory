package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextRulesTest {

    @Test
    void s2ReportsASecretNamedEnvOrArgWithALiteralValueByLineAndHashOnly() {
        assertThat(new SecretValue().check(Subjects.of("""
                FROM ubi9
                ENV DB_PASSWORD=hunter2
                ARG API_TOKEN=abc
                ENV PASSWORD_FILE=$SECRETS/db SECRET_KEY=
                ARG TOKEN
                """))).satisfiesExactly(
                        f -> {
                            assertThat(f.line()).isEqualTo(2);
                            assertThat(f.message()).contains("DB_PASSWORD").doesNotContain("hunter2");
                            assertThat(f.valueHash()).isEqualTo(Hashes.sha256("hunter2"));
                        },
                        f -> assertThat(f.line()).isEqualTo(3));
    }
}
