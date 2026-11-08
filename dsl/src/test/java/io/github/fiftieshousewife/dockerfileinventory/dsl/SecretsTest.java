package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SecretsTest {

    private static final String AWS_KEY = "AKIA" + "ABCDEFGHIJKLMNOP";
    private static final String JWT = "eyJ" + "hbGc.eyJzdWI.c2ln";

    @Test
    void secretIsAShapeUnderAnyNameOrAnyValueUnderASecretsNameExceptAPath() {
        assertAll(
                () -> assertThat(Secrets.secret("CONFIG", AWS_KEY)).isTrue(),
                () -> assertThat(Secrets.secret("DB_PASSWORD", "hunter2")).isTrue(),
                () -> assertThat(Secrets.secret("DB_PASSWORD", " \"/run/secrets/db\" ")).isFalse(),
                () -> assertThat(Secrets.secret("CONFIG", "hunter2")).isFalse());
    }

    @Test
    void secretNameMatchesKeyAsAWholePartAndTheLongerWordsAnywhere() {
        assertAll(
                () -> assertThat(Secrets.secretName("api.key")).isTrue(),
                () -> assertThat(Secrets.secretName("dbpassword")).isTrue(),
                () -> assertThat(Secrets.secretName("GITHUB_TOKEN")).isTrue(),
                () -> assertThat(Secrets.secretName("VALKEY_PREFIX")).isFalse(),
                () -> assertThat(Secrets.secretName("KEYCLOAK_URL")).isFalse());
    }

    @Test
    void secretShapeFindsAPrivateKeyAnAwsKeyIdOrAJwt() {
        assertAll(
                () -> assertThat(Secrets.secretShape("-----BEGIN RSA PRIVATE KEY-----")).isTrue(),
                () -> assertThat(Secrets.secretShape("id " + AWS_KEY + " here")).isTrue(),
                () -> assertThat(Secrets.secretShape("Bearer " + JWT)).isTrue(),
                () -> assertThat(Secrets.secretShape("/opt/app")).isFalse());
    }

    @Test
    void hideShapesReplacesEverySecretShapedValue() {
        assertThat(Secrets.hideShapes("A=" + AWS_KEY + " B=" + JWT + " C=1"))
                .isEqualTo("A=(hidden) B=(hidden) C=1");
    }

    @Test
    void aPlainValueIsShownAsItIs() {
        assertThat(Secrets.shown("summary", "Python 3.12")).isEqualTo("Python 3.12");
    }

    @Test
    void aSecretValueIsShownAsItsHashOnly() {
        assertThat(Secrets.shown("LEGACY_TOKEN", "abc123")).isEqualTo(
                "(hidden sha256:6ca13d52ca70c883e0f0bb101e425a89e8624de51db2d2392593af6a84118090)");
    }

    @Test
    void aValuesHashIsItsSha256InHex() {
        assertThat(Secrets.hash("abc123")).isEqualTo(
                "sha256:6ca13d52ca70c883e0f0bb101e425a89e8624de51db2d2392593af6a84118090");
    }
}
