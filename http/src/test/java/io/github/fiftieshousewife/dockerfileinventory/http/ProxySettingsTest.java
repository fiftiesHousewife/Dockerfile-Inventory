package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProxySettingsTest {

    @Test
    void anHttpsProxyVariableSendsHttpsCallsThroughIt() {
        ProxySettings settings = ProxySettings.from(Map.of("HTTPS_PROXY", "http://proxy.corp:8080"));

        assertThat(settings.selector().select(URI.create("https://ghe.corp/api/v3/")))
                .containsExactly(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.corp", 8080)));
    }

    @Test
    void lowerCaseVariablesWorkAndHttpCallsUseTheHttpProxy() {
        ProxySettings settings = ProxySettings.from(Map.of("http_proxy", "http://plain.corp:3128",
                "https_proxy", "http://secure.corp:3129"));

        assertThat(settings.selector().select(URI.create("http://jcr.corp/artifactory/")).getFirst().address())
                .isEqualTo(InetSocketAddress.createUnresolved("plain.corp", 3128));
        assertThat(settings.selector().select(URI.create("https://jcr.corp/artifactory/")).getFirst().address())
                .isEqualTo(InetSocketAddress.createUnresolved("secure.corp", 3129));
    }

    @Test
    void noProxyListsHostsDomainSuffixesAndAddressesThatGoDirect() {
        ProxySettings settings = ProxySettings.from(Map.of("HTTPS_PROXY", "http://proxy.corp:8080",
                "NO_PROXY", "localhost, .internal.corp,ghe.corp,10.0.0.5"));

        assertThat(settings.selector().select(URI.create("https://ghe.corp/"))).containsExactly(Proxy.NO_PROXY);
        assertThat(settings.selector().select(URI.create("https://jcr.internal.corp/")))
                .containsExactly(Proxy.NO_PROXY);
        assertThat(settings.selector().select(URI.create("https://10.0.0.5/"))).containsExactly(Proxy.NO_PROXY);
        assertThat(settings.selector().select(URI.create("https://github.com/")).getFirst().type())
                .isEqualTo(Proxy.Type.HTTP);
    }

    @Test
    void aNoProxyOfStarSendsEverythingDirectAndNoVariablesMeanDirect() {
        assertThat(ProxySettings.from(Map.of("HTTPS_PROXY", "http://proxy.corp:8080", "NO_PROXY", "*"))
                .selector().select(URI.create("https://github.com/"))).containsExactly(Proxy.NO_PROXY);
        assertThat(ProxySettings.from(Map.of()).selector().select(URI.create("https://github.com/")))
                .containsExactly(Proxy.NO_PROXY);
    }

    @Test
    void credentialsInTheProxyUrlAreKeptForTheProxyAndNeverShown() {
        ProxySettings settings = ProxySettings.from(Map.of("HTTPS_PROXY", "http://svc-scan:s3cr3t@proxy.corp:8080"));

        assertThat(settings.credentials()).hasValueSatisfying(c -> assertThat(c.user()).isEqualTo("svc-scan"));
        assertThat(settings.toString()).contains("proxy.corp:8080").doesNotContain("s3cr3t");
    }

    @Test
    void aMalformedProxyUrlIsRefusedNamingTheSetting() {
        assertThatThrownBy(() -> ProxySettings.from(Map.of("HTTPS_PROXY", "proxy.corp")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS_PROXY");
    }

    @Test
    void aProxyWithoutAPortIsOn8080() {
        assertThat(List.of(ProxySettings.address(URI.create("http://proxy.corp")),
                ProxySettings.address(URI.create("http://proxy.corp:3128"))))
                .containsExactly(InetSocketAddress.createUnresolved("proxy.corp", 8080),
                        InetSocketAddress.createUnresolved("proxy.corp", 3128));
    }

    @Test
    void credentialsDescribeTheirUserButNeverThePassword() {
        assertThat(new ProxySettings.Credentials("me", "s3cr3t")).hasToString("Credentials[user=me]");
    }
}
