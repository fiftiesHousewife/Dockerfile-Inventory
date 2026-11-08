package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Where repositories are cloned from: the web host behind discovery's GHE_API_URL (any of its forms; github.com
// when unset), with discovery's token when one is set. The token reaches git through its environment, so it is
// never on a command line or in a log.
final class CloneSource {

    private static final String DOT_COM = "github.com";

    private final String web;
    private final Optional<TokenSource> token;

    private CloneSource(String web, Optional<TokenSource> token) {
        this.web = web;
        this.token = token;
    }

    static CloneSource from(Map<String, String> environment) {
        String api = environment.get("GHE_API_URL");
        boolean tokened = environment.containsKey("GHE_TOKEN") || environment.containsKey("GHE_TOKEN_FILE");
        return new CloneSource(api == null || api.isBlank() ? "https://" + DOT_COM : web(api),
                tokened ? Optional.of(TokenSource.from(environment, "GHE_TOKEN")) : Optional.empty());
    }

    static String web(String api) {
        URI uri = URI.create(api.strip());
        String host = uri.getHost();
        if (host.equals(DOT_COM) || host.equals("api." + DOT_COM)) {
            return "https://" + DOT_COM;
        }
        if (host.endsWith(".ghe.com")) {
            return "https://" + (host.startsWith("api.") ? host.substring(4) : host);
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    String url(String repository) {
        return web + "/" + repository + ".git";
    }

    Map<String, String> gitEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>(Map.of("GIT_TERMINAL_PROMPT", "0"));
        token.ifPresent(source -> {
            String basic = Base64.getEncoder().encodeToString(("x-access-token:" + source.token())
                    .getBytes(StandardCharsets.UTF_8));
            environment.put("GIT_CONFIG_COUNT", "1");
            environment.put("GIT_CONFIG_KEY_0", "http.extraHeader");
            environment.put("GIT_CONFIG_VALUE_0", "Authorization: Basic " + basic);
        });
        return environment;
    }

    @Override
    public String toString() {
        return web + token.map(source -> " with " + source).orElse(", no token");
    }
}
