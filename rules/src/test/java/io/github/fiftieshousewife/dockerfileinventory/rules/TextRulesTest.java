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

    @Test
    void s2ReportsAKnownSecretShapeUnderAnyNameAndNeverKeepsTheValue() {
        String awsKey = "AKIA" + "ABCDEFGHIJKLMNOP";
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl";
        assertThat(new SecretValue().check(Subjects.of("FROM ubi9\nENV CONFIG=" + awsKey + "\nARG HEADER=" + jwt
                + "\nENV PEM=\"-----BEGIN RSA PRIVATE KEY-----\"\nENV GREETING=hello\n")))
                .hasSize(3)
                .allSatisfy(f -> assertThat(f.toString()).doesNotContain(awsKey).doesNotContain(jwt)
                        .doesNotContain("BEGIN RSA"));
    }

    @Test
    void s3ReportsCurlOrWgetPipedToAShellAcrossContinuations() {
        assertThat(new PipeToShell().check(Subjects.of("""
                FROM ubi9
                RUN curl -fsSL https://example.com/install.sh | sh
                RUN wget -qO- https://example.com/setup \\
                    | sudo bash -s -- --yes
                RUN curl -o /tmp/install.sh https://example.com/install.sh && sh /tmp/install.sh
                RUN curl -s https://example.com/data.json | jq .version
                """))).extracting(Finding::line).containsExactly(2, 3);
    }

    @Test
    void h3ReportsAdjacentShellFormRunsInOneStage() {
        assertThat(new AdjacentRuns().check(Subjects.of("""
                FROM ubi9 AS build
                RUN microdnf install -y make
                RUN make
                COPY . /src
                RUN make install
                RUN --mount=type=cache,target=/root/.m2 mvn package
                RUN ["echo", "exec form"]
                FROM ubi9
                RUN true
                """))).extracting(Finding::line).containsExactly(3);
    }
}
