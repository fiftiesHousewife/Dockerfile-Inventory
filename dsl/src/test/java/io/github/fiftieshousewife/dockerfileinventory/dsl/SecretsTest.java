package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretsTest {

    private static final String AWS_KEY = "AKIA" + "ABCDEFGHIJKLMNOP";
    private static final String JWT = "eyJ" + "hbGc.eyJzdWI.c2ln";

    @Test
    void secretIsAShapeUnderAnyNameOrAnyValueUnderASecretsNameExceptAPath() {
        assertThat(Secrets.secret("CONFIG", AWS_KEY)).isTrue();
        assertThat(Secrets.secret("DB_PASSWORD", "hunter2")).isTrue();
        assertThat(Secrets.secret("DB_PASSWORD", " \"/run/secrets/db\" ")).isFalse();
        assertThat(Secrets.secret("CONFIG", "hunter2")).isFalse();
    }

    @Test
    void secretNameMatchesKeyAsAWholePartAndTheLongerWordsAnywhere() {
        assertThat(Secrets.secretName("api.key")).isTrue();
        assertThat(Secrets.secretName("dbpassword")).isTrue();
        assertThat(Secrets.secretName("GITHUB_TOKEN")).isTrue();
        assertThat(Secrets.secretName("VALKEY_PREFIX")).isFalse();
        assertThat(Secrets.secretName("KEYCLOAK_URL")).isFalse();
    }

    @Test
    void secretShapeFindsAPrivateKeyAnAwsKeyIdOrAJwt() {
        assertThat(Secrets.secretShape("-----BEGIN RSA PRIVATE KEY-----")).isTrue();
        assertThat(Secrets.secretShape("id " + AWS_KEY + " here")).isTrue();
        assertThat(Secrets.secretShape("Bearer " + JWT)).isTrue();
        assertThat(Secrets.secretShape("/opt/app")).isFalse();
    }

    @Test
    void hideShapesReplacesEverySecretShapedValue() {
        assertThat(Secrets.hideShapes("A=" + AWS_KEY + " B=" + JWT + " C=1"))
                .isEqualTo("A=(hidden) B=(hidden) C=1");
    }
}
