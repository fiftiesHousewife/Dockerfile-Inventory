package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpSettingsTest {

    @Test
    void withNothingSetTheDefaultsApply() {
        assertThat(HttpSettings.from(Map.of())).satisfies(settings -> {
            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(settings.requestTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(settings.retries()).isEqualTo(new RetryPolicy(5, Duration.ofHours(1)));
            assertThat(settings.trustBundle()).isEmpty();
            assertThat(settings.proxy().https()).isEmpty();
        });
    }

    @Test
    void everySettingIsReadFromItsVariable() {
        HttpSettings settings = HttpSettings.from(Map.of("CONNECT_TIMEOUT", "PT5S", "HTTP_TIMEOUT", "PT2M",
                "HTTP_RETRIES", "3", "MAX_RATE_LIMIT_WAIT", "PT15M",
                "TRUST_BUNDLE", "src/test/resources/fixtures/example-corp-ca.pem",
                "HTTPS_PROXY", "http://proxy.corp:8080"));

        assertThat(settings).satisfies(s -> {
            assertThat(s.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(s.requestTimeout()).isEqualTo(Duration.ofMinutes(2));
            assertThat(s.retries()).isEqualTo(new RetryPolicy(3, Duration.ofMinutes(15)));
            assertThat(s.trustBundle()).isPresent();
            assertThat(s.client().proxy()).isPresent();
        });
    }

    @Test
    void aMalformedValueIsRefusedNamingItsVariable() {
        assertThatThrownBy(() -> HttpSettings.from(Map.of("HTTP_TIMEOUT", "60s")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTP_TIMEOUT");
        assertThatThrownBy(() -> HttpSettings.from(Map.of("HTTP_RETRIES", "0")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTP_RETRIES");
    }

    @Test
    void theDescriptionNamesEverySettingButNoProxyPassword() {
        String described = HttpSettings.from(Map.of("HTTPS_PROXY", "http://svc:s3cr3t@proxy.corp:8080")).toString();

        assertThat(described).contains("connect timeout PT10S", "request timeout PT1M", "5 attempts",
                "proxy.corp:8080", "trust bundle: the JDK's").doesNotContain("s3cr3t");
    }

    @Test
    void theClientCarriesTheConnectTimeoutAndProxy() {
        var client = HttpSettings.from(Map.of("HTTPS_PROXY", "http://proxy.corp:8080")).client();

        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(10));
        assertThat(client.proxy().orElseThrow().select(URI.create("https://github.com/")).getFirst().address())
                .hasToString("proxy.corp/<unresolved>:8080");
    }

    @Test
    void theProxyAuthenticatorAnswersOnlyTheProxyWithItsCredentials() throws Exception {
        Authenticator authenticator = HttpSettings.proxyAuthenticator(new ProxySettings.Credentials("me", "secret"));
        URL url = URI.create("https://ghe.corp/").toURL();

        assertAll(() -> assertThat(authenticator.requestPasswordAuthenticationInstance("proxy.corp", null, 8080,
                        "https", "", "Basic", url, Authenticator.RequestorType.PROXY))
                        .extracting(PasswordAuthentication::getUserName).isEqualTo("me"),
                () -> assertThat(authenticator.requestPasswordAuthenticationInstance("ghe.corp", null, 443,
                        "https", "", "Basic", url, Authenticator.RequestorType.SERVER)).isNull());
    }
}
