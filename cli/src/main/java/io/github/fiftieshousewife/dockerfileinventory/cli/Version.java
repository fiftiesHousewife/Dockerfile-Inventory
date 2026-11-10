package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

final class Version {

    private Version() {
    }

    static String current() {
        return read(Version.class.getResourceAsStream("/docker-inventory.properties"));
    }

    static String read(InputStream properties) {
        try (InputStream in = properties) {
            Properties read = new Properties();
            read.load(in);
            return read.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
