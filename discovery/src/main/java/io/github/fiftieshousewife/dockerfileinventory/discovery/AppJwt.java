package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// GitHub accepts an app's JWT for ten minutes at most.
final class AppJwt {

    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN (RSA )?PRIVATE KEY-----([A-Za-z0-9+/=\\s]+)-----END (RSA )?PRIVATE KEY-----");
    // The algorithm identifier that wraps a PKCS#1 RSA key into PKCS#8: rsaEncryption with no parameters.
    private static final byte[] RSA_ALGORITHM = HexFormat.of().parseHex("300d06092a864886f70d0101010500");
    private static final long BACKDATED = 60;
    private static final long LASTING = 540;

    private AppJwt() {
    }

    static PrivateKey key(String pem) {
        Matcher match = PEM.matcher(pem);
        if (!match.find()) {
            throw new IllegalStateException("GHE_APP_PRIVATE_KEY_FILE holds no PEM private key (BEGIN RSA PRIVATE KEY "
                    + "or BEGIN PRIVATE KEY)");
        }
        byte[] der = Base64.getMimeDecoder().decode(match.group(2));
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                    match.group(1) == null ? der : pkcs8(der)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("GHE_APP_PRIVATE_KEY_FILE holds a key Java cannot read as RSA", e);
        }
    }

    static byte[] pkcs8(byte[] pkcs1) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(new byte[] {0x02, 0x01, 0x00});
        body.writeBytes(RSA_ALGORITHM);
        body.writeBytes(tagged(0x04, pkcs1));
        return tagged(0x30, body.toByteArray());
    }

    static byte[] tagged(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = content.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            byte[] size = BigInteger.valueOf(length).toByteArray();
            int start = size[0] == 0 ? 1 : 0;
            out.write(0x80 | (size.length - start));
            out.write(size, start, size.length - start);
        }
        out.writeBytes(content);
        return out.toByteArray();
    }

    static String of(String appId, PrivateKey key, Instant now) {
        String signed = encoded("{\"alg\":\"RS256\",\"typ\":\"JWT\"}") + "." + encoded("{\"iat\":"
                + (now.getEpochSecond() - BACKDATED) + ",\"exp\":" + (now.getEpochSecond() + LASTING) + ",\"iss\":\""
                + appId + "\"}");
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            return signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot sign the GitHub App's JWT", e);
        }
    }

    private static String encoded(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
