package io.github.fiftieshousewife.dockerfileinventory.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The outbound proxy from {@code HTTPS_PROXY}, {@code HTTP_PROXY} and {@code NO_PROXY} (either case), which a
 * corporate shell and OpenShift's cluster-wide proxy set and {@code java.net.http} ignores. Credentials in the proxy
 * URL are kept for the proxy's challenge only and never shown.
 */
public record ProxySettings(Optional<InetSocketAddress> https, Optional<InetSocketAddress> http,
                            List<String> direct, Optional<Credentials> credentials) {

    public record Credentials(String user, String password) {

        @Override
        public String toString() {
            return "Credentials[user=" + user + "]";
        }
    }

    public ProxySettings {
        direct = List.copyOf(direct);
    }

    public static ProxySettings from(Map<String, String> environment) {
        Optional<URI> https = url(environment, "HTTPS_PROXY");
        Optional<URI> http = url(environment, "HTTP_PROXY");
        return new ProxySettings(https.map(ProxySettings::address), http.map(ProxySettings::address),
                noProxy(variable(environment, "NO_PROXY").orElse("")),
                https.or(() -> http).flatMap(ProxySettings::credentials));
    }

    public ProxySelector selector() {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                Optional<InetSocketAddress> proxy = "http".equals(uri.getScheme()) ? http.or(() -> https) : https;
                return proxy.filter(p -> !goesDirect(uri.getHost()))
                        .map(p -> List.of(new Proxy(Proxy.Type.HTTP, p))).orElse(List.of(Proxy.NO_PROXY));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException e) {
            }
        };
    }

    boolean goesDirect(String host) {
        return direct.stream().anyMatch(entry -> entry.equals("*") || host.equals(entry)
                || entry.startsWith(".") && (host.endsWith(entry) || host.equals(entry.substring(1))));
    }

    @Override
    public String toString() {
        return "proxy https=" + https.map(ProxySettings::shown).orElse("none") + " http="
                + http.map(ProxySettings::shown).orElse("none") + " direct=" + direct;
    }

    static List<String> noProxy(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(entry -> !entry.isEmpty())
                .map(entry -> entry.startsWith("*.") ? entry.substring(1) : entry).toList();
    }

    static Optional<String> variable(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name)).or(() -> Optional.ofNullable(environment.get(
                name.toLowerCase(Locale.ROOT)))).map(String::strip).filter(value -> !value.isEmpty());
    }

    private static Optional<URI> url(Map<String, String> environment, String name) {
        return variable(environment, name).map(value -> parsed(name, value));
    }

    static URI parsed(String name, String value) {
        try {
            URI uri = new URI(value);
            if (uri.getHost() == null || uri.getScheme() == null) {
                throw new IllegalArgumentException(name + " is not a URL like http://proxy:8080: " + withoutUser(value));
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(name + " is not a URL: " + withoutUser(value), e);
        }
    }

    private static InetSocketAddress address(URI uri) {
        return InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort() < 0 ? 8080 : uri.getPort());
    }

    static Optional<Credentials> credentials(URI uri) {
        String info = uri.getUserInfo();
        if (info == null || info.isEmpty()) {
            return Optional.empty();
        }
        int colon = info.indexOf(':');
        return Optional.of(colon < 0 ? new Credentials(info, "")
                : new Credentials(info.substring(0, colon), info.substring(colon + 1)));
    }

    private static String shown(InetSocketAddress address) {
        return address.getHostString() + ":" + address.getPort();
    }

    private static String withoutUser(String value) {
        return value.replaceFirst("//[^@/]*@", "//");
    }
}
