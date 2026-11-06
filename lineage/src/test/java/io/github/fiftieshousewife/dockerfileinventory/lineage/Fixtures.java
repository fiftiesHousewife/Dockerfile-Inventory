package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class Fixtures {

    private Fixtures() {
    }

    static String artifactory(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/artifactory/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
