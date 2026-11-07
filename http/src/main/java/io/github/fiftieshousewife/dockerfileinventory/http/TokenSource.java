package io.github.fiftieshousewife.dockerfileinventory.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Optional;

/**
 * A token from the environment: {@code NAME}, or {@code NAME_FILE} naming a file that is read again whenever it
 * changes, so a token rotated by OpenShift, a Vault agent or a refresher is picked up during a long run. Never shown.
 */
public final class TokenSource {

    private final String name;
    private final String fixed;
    private final Path file;
    private final Object lock = new Object();
    private String token;
    private FileTime readAt;

    private TokenSource(String name, String fixed, Path file) {
        this.name = name;
        this.fixed = fixed;
        this.file = file;
    }

    public static TokenSource from(Map<String, String> environment, String name) {
        Optional<String> variable = present(environment.get(name));
        Optional<String> file = present(environment.get(name + "_FILE"));
        if (variable.isPresent() && file.isPresent()) {
            throw new IllegalArgumentException("set " + name + " or " + name + "_FILE, not both");
        }
        if (variable.isEmpty() && file.isEmpty()) {
            throw new IllegalArgumentException(name + " or " + name + "_FILE is not set");
        }
        return new TokenSource(name, variable.map(String::strip).orElse(null), file.map(Path::of).orElse(null));
    }

    public String token() {
        if (fixed != null) {
            return fixed;
        }
        synchronized (lock) {
            if (token == null || !modified().equals(readAt)) {
                read();
            }
            return token;
        }
    }

    /** After a 401: true when the file now holds another token, so the call is worth one more try. */
    public boolean refresh() {
        if (fixed != null) {
            return false;
        }
        synchronized (lock) {
            String before = token;
            read();
            return !token.equals(before);
        }
    }

    @Override
    public String toString() {
        return fixed != null ? name + " (variable)" : name + "_FILE " + file;
    }

    private void read() {
        try {
            readAt = modified();
            String read = Files.readString(file).strip();
            if (read.isEmpty()) {
                throw new IllegalStateException(name + "_FILE is empty: " + file);
            }
            token = read;
        } catch (IOException e) {
            throw new UncheckedIOException(name + "_FILE cannot be read: " + file, e);
        }
    }

    private FileTime modified() {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            throw new UncheckedIOException(name + "_FILE cannot be read: " + file, e);
        }
    }

    private static Optional<String> present(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }
}
