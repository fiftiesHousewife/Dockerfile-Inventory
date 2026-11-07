package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HiddenSecretsTest {

    @Test
    void secretValuesAreHiddenInEitherFormAndSecretShapesAnywhere() {
        assertThat(HiddenSecrets.hidden("""
                FROM ubi9
                ENV DB_PASSWORD=hunter2 APP_NAME=shop
                ARG NPM_TOKEN="abc def"
                ENV API_KEY s3cr3t
                LABEL build.secret='x'
                RUN aws configure set key AKIAIOSFODNN7EXAMPLE && echo done
                """)).isEqualTo("""
                FROM ubi9
                ENV DB_PASSWORD=(hidden) APP_NAME=shop
                ARG NPM_TOKEN=(hidden)
                ENV API_KEY (hidden)
                LABEL build.secret=(hidden)
                RUN aws configure set key (hidden) && echo done
                """);
    }

    @Test
    void aSecretNamedValueThatIsOnlyAnAbsolutePathIsShown() {
        assertThat(HiddenSecrets.hidden("ENV NSS_WRAPPER_PASSWD=\"/opt/app/nss_passwd\" DB_PASSWORD=x\n"
                + "ENV CREDENTIAL_FILE /run/creds.json"))
                .isEqualTo("ENV NSS_WRAPPER_PASSWD=\"/opt/app/nss_passwd\" DB_PASSWORD=(hidden)\n"
                        + "ENV CREDENTIAL_FILE /run/creds.json");
    }

    @Test
    void aContinuationLineOfAnEnvIsReadForPairsToo() {
        assertThat(HiddenSecrets.hidden("ENV A=1 \\\n    DB_PASSWORD=x\nRUN DB_PASSWORD=y"))
                .isEqualTo("ENV A=1 \\\n    DB_PASSWORD=(hidden)\nRUN DB_PASSWORD=y");
    }

    @Test
    void theLegacyFormIsReadOnlyOnTheInstructionsFirstLine() {
        assertThat(HiddenSecrets.hidePairs("ENV API_KEY s3cr3t", true)).isEqualTo("ENV API_KEY (hidden)");
        assertThat(HiddenSecrets.hidePairs("ENV API_KEY s3cr3t", false)).isEqualTo("ENV API_KEY s3cr3t");
        assertThat(HiddenSecrets.hidePairs("ENV APP_NAME shop", true)).isEqualTo("ENV APP_NAME shop");
    }
}
