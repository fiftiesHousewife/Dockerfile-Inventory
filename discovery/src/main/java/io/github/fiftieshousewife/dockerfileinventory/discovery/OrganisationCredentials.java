package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class OrganisationCredentials {

    interface Installations {

        Map<String, Long> list();

        InstallationToken.Issued issue(long installation);
    }

    private final Optional<Installations> app;
    private final Optional<Credential> token;
    private final Supplier<Instant> now;
    private final Map<String, Credential> credentials = new LinkedHashMap<>();
    private final Map<String, String> chosen = new LinkedHashMap<>();
    private Map<String, Long> installations;

    OrganisationCredentials(Optional<Installations> app, Optional<Credential> token, Supplier<Instant> now) {
        this.app = app;
        this.token = token;
        this.now = now;
    }

    synchronized Credential of(String organisation) {
        String org = organisation.toLowerCase(Locale.ROOT);
        return credentials.computeIfAbsent(org, this::choose);
    }

    Credential choose(String org) {
        Long installation = installations().get(org);
        if (installation != null) {
            chosen.put(org, "the GitHub App, installation " + installation);
            return new InstallationToken(() -> app.orElseThrow().issue(installation), now);
        }
        Credential fallback = token.orElseThrow(() -> new IllegalStateException("no credential reads " + org + ": "
                + (app.isPresent() ? "the GitHub App is not installed there, and neither GHE_TOKEN nor "
                + "GHE_TOKEN_FILE is set" : "neither GHE_TOKEN, GHE_TOKEN_FILE nor GHE_APP_ID is set")));
        chosen.put(org, fallback.toString());
        return fallback;
    }

    synchronized Map<String, Long> installations() {
        if (installations == null) {
            installations = app.map(Installations::list).orElse(Map.of());
        }
        return installations;
    }

    List<String> installed() {
        return installations().keySet().stream().sorted().toList();
    }

    Optional<Credential> token() {
        return token;
    }

    synchronized Map<String, String> chosen() {
        return new LinkedHashMap<>(chosen);
    }
}
