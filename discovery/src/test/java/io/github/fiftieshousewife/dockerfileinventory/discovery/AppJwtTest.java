package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AppJwtTest {

    static final KeyPair KEYS = keys();

    private static KeyPair keys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der) + "\n-----END " + type + "-----\n";
    }

    // PKCS#8 wraps PKCS#1 in 26 bytes for an RSA key of this size: what GitHub hands out is the part inside.
    static byte[] pkcs1() {
        byte[] pkcs8 = KEYS.getPrivate().getEncoded();
        return Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
    }

    private static JsonNode part(String jwt, int index) {
        return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[index]));
    }

    @Test
    void theKeyGitHubHandsOutAndAPkcs8KeyAreBothRead() {
        assertAll(
                () -> assertThat(AppJwt.key(pem("RSA PRIVATE KEY", pkcs1())).getEncoded())
                        .isEqualTo(KEYS.getPrivate().getEncoded()),
                () -> assertThat(AppJwt.key(pem("PRIVATE KEY", KEYS.getPrivate().getEncoded())).getEncoded())
                        .isEqualTo(KEYS.getPrivate().getEncoded()));
    }

    @Test
    void aFileThatIsNoKeyIsRefused() {
        assertThatThrownBy(() -> AppJwt.key("not a key")).hasMessage("GHE_APP_PRIVATE_KEY_FILE holds no PEM private "
                + "key (BEGIN RSA PRIVATE KEY or BEGIN PRIVATE KEY)");
    }

    @Test
    void theJwtNamesTheAppAndLastsUnderTenMinutesFromAMinuteAgo() {
        String jwt = AppJwt.of("1234", KEYS.getPrivate(), Instant.ofEpochSecond(1_000_000));

        assertAll(
                () -> assertThat(part(jwt, 0).path("alg").asString()).isEqualTo("RS256"),
                () -> assertThat(part(jwt, 1).path("iss").asString()).isEqualTo("1234"),
                () -> assertThat(part(jwt, 1).path("iat").asLong()).isEqualTo(999_940),
                () -> assertThat(part(jwt, 1).path("exp").asLong()).isEqualTo(1_000_540));
    }

    @Test
    void theJwtIsSignedWithTheAppsKey() throws GeneralSecurityException {
        String jwt = AppJwt.of("1234", KEYS.getPrivate(), Instant.now());
        String[] parts = jwt.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KEYS.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }
}
