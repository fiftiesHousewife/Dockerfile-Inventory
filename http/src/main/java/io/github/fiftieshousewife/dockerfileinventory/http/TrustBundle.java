package io.github.fiftieshousewife.dockerfileinventory.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * {@code TRUST_BUNDLE}: PEM certificates (a corporate CA, a TLS-inspecting proxy's) trusted in addition to the JDK's
 * own, so an internal GitHub Enterprise or Artifactory is reachable without rebuilding the JVM's trust store.
 */
public final class TrustBundle {

    private TrustBundle() {
    }

    public static SSLContext sslContext(Path bundle) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new javax.net.ssl.TrustManager[] {trustManager(bundle)}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TLS could not be set up with TRUST_BUNDLE " + bundle, e);
        }
    }

    static X509TrustManager trustManager(Path bundle) {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            List<X509Certificate> certificates = Stream.concat(defaults().stream(), read(bundle).stream()).toList();
            for (int i = 0; i < certificates.size(); i++) {
                store.setCertificateEntry("ca-" + i, certificates.get(i));
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            return onlyX509(factory);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("TLS could not be set up with TRUST_BUNDLE " + bundle, e);
        }
    }

    static List<X509Certificate> read(Path bundle) {
        try (InputStream in = Files.newInputStream(bundle)) {
            Collection<? extends Certificate> read = CertificateFactory.getInstance("X.509").generateCertificates(in);
            List<X509Certificate> certificates = read.stream().map(X509Certificate.class::cast).toList();
            if (certificates.isEmpty()) {
                throw new IllegalArgumentException("TRUST_BUNDLE holds no PEM certificate: " + bundle);
            }
            return certificates;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("TRUST_BUNDLE cannot be read as PEM certificates: " + bundle, e);
        }
    }

    private static List<X509Certificate> defaults() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        return Arrays.asList(onlyX509(factory).getAcceptedIssuers());
    }

    static X509TrustManager onlyX509(TrustManagerFactory factory) {
        return Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("the JDK offers no X.509 trust manager"));
    }
}
