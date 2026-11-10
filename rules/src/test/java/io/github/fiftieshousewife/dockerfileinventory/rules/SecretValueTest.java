package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.secrets.Secrets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class SecretValueTest {

    @Test
    void s2ReportsASecretNamedEnvOrArgWithALiteralValueByLineAndHashOnly() {
        List<Finding> findings = new SecretValue().check(Subjects.of("""
                FROM ubi9
                ENV DB_PASSWORD=hunter2
                ARG API_TOKEN=abc
                ENV PASSWORD_FILE=$SECRETS/db SECRET_KEY=
                ARG TOKEN
                """));

        assertAll(
                () -> assertThat(findings).extracting(Finding::line).containsExactly(2, 3),
                () -> assertThat(findings.getFirst().message()).contains("DB_PASSWORD").doesNotContain("hunter2"),
                () -> assertThat(findings.getFirst().valueHash()).isEqualTo(SecretValue.sha256("hunter2")));
    }

    @Test
    void s2LeavesASecretNamedValueThatIsOnlyAnAbsolutePathAlone() {
        assertThat(new SecretValue().check(Subjects.of("""
                FROM ubi9
                ENV NSS_WRAPPER_PASSWD="/opt/app/nss_passwd" CREDENTIAL_FILE=/run/creds.json
                ENV DB_PASSWORD="/not a path"
                """))).singleElement().extracting(Finding::message).asString().contains("DB_PASSWORD");
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
    void s2IgnoresAFileThatDidNotParse() {
        assertThat(new SecretValue().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "VALKEY_PREFIX, false", "KEYCLOAK_URL, false", "MONKEY, false", "PUBLIC_KEYRING, false",
        "API_KEY, true", "KEY, true", "DB_PASSWORD, true", "DBPASSWORD, true", "GITHUB_TOKEN, true",
        "client-secret, true", "aws.credentials, true", "ssh_passwd, true"})
    void secretNamesMatchByTheirPartsSoKeyMustBeAWholePart(String name, boolean secret) {
        assertThat(Secrets.secretName(name)).isEqualTo(secret);
    }

    @Test
    void instructionsAreTheGlobalArgsThenEveryStagesInstructionsInOrder() {
        Subject subject = Subjects.of("ARG V=1\nFROM ubi9 AS a\nENV A=1\nFROM ubi9\nUSER 1001\n");

        assertThat(SecretValue.instructions(subject.file()))
                .extracting(i -> i.getClass().getSimpleName())
                .containsExactly("Arg", "Env", "User");
    }

    @Test
    void findingsReadEveryPairOfAnEnvAndNothingFromOtherInstructions() {
        Subject subject = Subjects.of("FROM ubi9\nENV A_TOKEN=abc B_PASSWORD=def\nUSER 1001\n");
        List<Docker.Instruction> instructions = subject.file().getStages().getFirst().getInstructions();

        assertThat(new SecretValue().findings(subject, 2, instructions.get(0)))
                .extracting(Finding::message)
                .containsExactly("ENV A_TOKEN has a secret-shaped value", "ENV B_PASSWORD has a secret-shaped value");
        assertThat(new SecretValue().findings(subject, 3, instructions.get(1))).isEmpty();
    }

    @Test
    void checkKeepsTheLineAndTheHashOfASecretValue() {
        Subject subject = Subjects.of("FROM ubi9\nARG API_TOKEN=abc\n");

        assertThat(new SecretValue().check(subject, 2, "ARG", "API_TOKEN", argValue(subject))).hasValueSatisfying(
                f -> assertThat(f).isEqualTo(new Finding("S2", Severity.CRITICAL, "blob", 2,
                        "ARG API_TOKEN has a secret-shaped value", SecretValue.sha256("abc"), List.of())));
        assertThat(new SecretValue().check(subject, 2, "ARG", "GREETING", argValue(subject))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ARG A=abc|abc", "ARG A=\"abc\"|abc", "ARG A='a$b'|a$b", "ARG A=$B|", "ARG A=x${B}|", "ARG A=\"\"|"})
    void literalIsTheWrittenOutValueWithoutQuotesAndNoneWhenAVariableIsInIt(String arg, String expected) {
        assertThat(SecretValue.literal(argValue(Subjects.of("FROM ubi9\n" + arg + "\n"))).orElse(null))
                .isEqualTo(expected);
    }

    @Test
    void literalIsNoneForAnArgWithoutAValue() {
        assertThat(SecretValue.literal(argValue(Subjects.of("FROM ubi9\nARG A\n")))).isEmpty();
    }

    @Test
    void literalIsNoneForAnEmptyEnvValue() {
        Subject subject = Subjects.of("FROM ubi9\nENV SECRET_KEY=\n");
        Docker.Env env = (Docker.Env) subject.file().getStages().getFirst().getInstructions().getFirst();

        assertThat(SecretValue.literal(env.getPairs().getFirst().getValue())).isEmpty();
    }

    private static Docker.Argument argValue(Subject subject) {
        return ((Docker.Arg) subject.file().getStages().getFirst().getInstructions().getFirst()).getValue();
    }

    @Test
    void sha256IsTheLowerCaseHexDigestOfTheUtf8Text() {
        assertThat(SecretValue.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
