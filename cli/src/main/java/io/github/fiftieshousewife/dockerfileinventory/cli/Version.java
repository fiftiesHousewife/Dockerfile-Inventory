package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

final class Version {

    private Version() {
    }

    static String current() {
        try (InputStream in = Version.class.getResourceAsStream("/docker-inventory.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
