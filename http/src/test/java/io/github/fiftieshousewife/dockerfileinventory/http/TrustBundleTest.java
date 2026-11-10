package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustBundleTest {

    private static final Path CORPORATE_CA = Path.of("src/test/resources/fixtures/example-corp-ca.pem");

    @Test
    void aBundlesCertificatesAreTrustedAlongsideTheJdksOwn() {
        var issuers = TrustBundle.trustManager(CORPORATE_CA).getAcceptedIssuers();

        assertThat(Arrays.stream(issuers).map(c -> c.getSubjectX500Principal().getName()))
                .contains("CN=Example Corp Root CA").hasSizeGreaterThan(10);
    }

    @Test
    void anUnreadableOrEmptyBundleIsRefusedNamingTheSetting(@TempDir Path dir) throws IOException {
        Path empty = Files.writeString(dir.resolve("empty.pem"), "not a certificate");

        assertThatThrownBy(() -> TrustBundle.trustManager(dir.resolve("missing.pem")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TRUST_BUNDLE");
        assertThatThrownBy(() -> TrustBundle.trustManager(empty))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TRUST_BUNDLE");
    }

    @Test
    void anSslContextIsMadeFromTheBundle() {
        assertThat(TrustBundle.sslContext(CORPORATE_CA).getProtocol()).isEqualTo("TLS");
    }

    @Test
    void theX509TrustManagerIsTakenFromAFactory() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);

        assertThat(TrustBundle.onlyX509(factory).getAcceptedIssuers()).isNotEmpty();
    }
}
