package io.github.fiftieshousewife.dockerfileinventory.http;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * How a stage talks to GitHub Enterprise or Artifactory inside a corporate network, from the environment: timeouts,
 * retries, the longest rate-limit wait, the outbound proxy and a corporate CA. Every value has a default and a bad
 * one stops the run naming its variable.
 */
public record HttpSettings(Duration connectTimeout, Duration requestTimeout, RetryPolicy retries,
                           ProxySettings proxy, Optional<Path> trustBundle) {

    public static HttpSettings from(Map<String, String> environment) {
        return new HttpSettings(duration(environment, "CONNECT_TIMEOUT", Duration.ofSeconds(10)),
                duration(environment, "HTTP_TIMEOUT", Duration.ofSeconds(60)),
                new RetryPolicy(attempts(environment),
                        duration(environment, "MAX_RATE_LIMIT_WAIT", Duration.ofHours(1))),
                ProxySettings.from(environment),
                ProxySettings.variable(environment, "TRUST_BUNDLE").map(Path::of));
    }

    public HttpClient client() {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy.https().isPresent() || proxy.http().isPresent()) {
            builder.proxy(proxy.selector());
        }
        proxy.credentials().ifPresent(credentials -> builder.authenticator(proxyAuthenticator(credentials)));
        trustBundle.ifPresent(bundle -> builder.sslContext(TrustBundle.sslContext(bundle)));
        return builder.build();
    }

    @Override
    public String toString() {
        return "connect timeout " + connectTimeout + ", request timeout " + requestTimeout + ", "
                + retries.attempts() + " attempts, rate-limit waits up to " + retries.maxRateLimitWait() + ", "
                + proxy + ", trust bundle: " + trustBundle.map(Path::toString).orElse("the JDK's");
    }

    static Duration duration(Map<String, String> environment, String name, Duration fallback) {
        return ProxySettings.variable(environment, name).map(value -> {
            try {
                return Duration.parse(value);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(name + " is not an ISO-8601 duration like PT60S: " + value, e);
            }
        }).orElse(fallback);
    }

    static int attempts(Map<String, String> environment) {
        return ProxySettings.variable(environment, "HTTP_RETRIES").map(value -> {
            try {
                int attempts = Integer.parseInt(value);
                if (attempts >= 1) {
                    return attempts;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("HTTP_RETRIES is not a whole number: " + value, e);
            }
            throw new IllegalArgumentException("HTTP_RETRIES must be 1 or more: " + value);
        }).orElse(5);
    }

    static Authenticator proxyAuthenticator(ProxySettings.Credentials credentials) {
        // the JDK refuses Basic on a proxy's CONNECT unless this is cleared; it must be set before the first call
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return getRequestorType() == RequestorType.PROXY
                        ? new PasswordAuthentication(credentials.user(), credentials.password().toCharArray()) : null;
            }
        };
    }
}
