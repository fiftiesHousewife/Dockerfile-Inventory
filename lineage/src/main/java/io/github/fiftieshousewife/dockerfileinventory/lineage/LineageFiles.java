package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/**
 * The lineage stage's files under its output directory: {@code images.jsonl}, appended one row at a time so an
 * interrupted inventory resumes, and {@code configs/}, each image config stored once under its digest.
 */
public final class LineageFiles {

    private static final String TAGS = "images.jsonl";

    private final Path dir;
    private final ObjectMapper json = new ObjectMapper();

    public LineageFiles(Path dir) {
        this.dir = dir;
    }

    public synchronized void record(TagRow row) {
        append(TAGS, json.writeValueAsString(row));
    }

    public List<TagRow> tags() {
        return lines(TAGS).stream().map(line -> json.readValue(line, TagRow.class)).toList();
    }

    /** Replaces a whole output file; the analysis rewrites every file from the inventory each run. */
    public void write(String file, List<String> lines) {
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(file), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean hasConfig(String digest) {
        return Files.exists(configPath(digest));
    }

    public Optional<String> config(String digest) {
        try {
            return hasConfig(digest) ? Optional.of(Files.readString(configPath(digest))) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Written under a temporary name and moved, so a config file on disk is always whole. */
    public void storeConfig(String digest, String text) {
        try {
            Path target = configPath(digest);
            Files.createDirectories(target.getParent());
            Path part = target.resolveSibling(target.getFileName() + ".part");
            Files.writeString(part, text, StandardCharsets.UTF_8);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path configPath(String digest) {
        return dir.resolve("configs").resolve(digest.replace(':', '_') + ".json");
    }

    private void append(String file, String line) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(file), line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lines(String file) {
        try {
            Path path = dir.resolve(file);
            return Files.exists(path) ? Files.readAllLines(path).stream().filter(l -> !l.isBlank()).toList() : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
