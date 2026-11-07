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
    void s2LeavesASecretNamedValueThatIsOnlyAnAbsolutePathAlone() {
        assertThat(new SecretValue().check(Subjects.of("""
                FROM ubi9
                ENV NSS_WRAPPER_PASSWD="/opt/app/nss_passwd" CREDENTIAL_FILE=/run/creds.json
                ENV DB_PASSWORD="/not a path"
                """))).singleElement().satisfies(f -> assertThat(f.message()).contains("DB_PASSWORD"));
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

    @Test
    void r3ReportsAFinalImageWithoutOciRevisionOrSourceCountingEarlierStagesItBuildsOn() {
        Rule r3 = new MissingOciLabels();

        assertThat(r3.check(Subjects.of("FROM ubi9\nLABEL org.opencontainers.image.source=https://x\n")))
                .singleElement().satisfies(f -> assertThat(f.message())
                        .contains("org.opencontainers.image.revision").doesNotContain("image.source"));
        assertThat(r3.check(Subjects.of("""
                FROM ubi9 AS labelled
                LABEL org.opencontainers.image.revision=$REV org.opencontainers.image.source=$SRC
                FROM golang AS build
                FROM labelled
                USER 1001
                """))).isEmpty();
        assertThat(r3.check(Subjects.of("""
                FROM ubi9
                # the build sets org.opencontainers.image.revision
                LABEL org.opencontainers.image.source=$SRC
                """))).hasSize(1);
        assertThat(r3.check(Subjects.of("""
                FROM ubi9 AS build
                LABEL org.opencontainers.image.revision=$REV org.opencontainers.image.source=$SRC
                FROM ubi9
                """))).hasSize(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "VALKEY_PREFIX, false", "KEYCLOAK_URL, false", "MONKEY, false", "PUBLIC_KEYRING, false",
        "API_KEY, true", "KEY, true", "DB_PASSWORD, true", "DBPASSWORD, true", "GITHUB_TOKEN, true",
        "client-secret, true", "aws.credentials, true", "ssh_passwd, true"})
    void secretNamesMatchByTheirPartsSoKeyMustBeAWholePart(String name, boolean secret) {
        assertThat(SecretValue.secretName(name)).isEqualTo(secret);
    }
}
