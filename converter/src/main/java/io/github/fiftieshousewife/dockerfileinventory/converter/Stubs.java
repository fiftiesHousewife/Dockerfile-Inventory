package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class Stubs {

    private Stubs() {
    }

    static List<String> create(Path context, List<String> sources, String arch) {
        return sources.stream()
                .map(source -> expanded(source, arch))
                .flatMap(path -> stubbed(context, path).stream())
                .toList();
    }

    static Optional<String> stubbed(Path context, String path) {
        if (exists(context, path)) {
            return Optional.empty();
        }
        String stub = path.replace("*", "stub").replace("?", "s");
        write(context, stub);
        return Optional.of(stub);
    }

    static String expanded(String source, String arch) {
        String path = source.replace("${TARGETOS}", "linux").replace("$TARGETOS", "linux")
                .replace("${TARGETARCH}", arch).replace("$TARGETARCH", arch);
        return path.startsWith("/") ? path.substring(1) : path;
    }

    static boolean exists(Path context, String path) {
        Path resolved = context.resolve(path.isEmpty() ? "." : path);
        if (!path.contains("*") && !path.contains("?")) {
            return Files.exists(resolved);
        }
        Path directory = resolved.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        try (DirectoryStream<Path> matches = Files.newDirectoryStream(directory,
                String.valueOf(resolved.getFileName()))) {
            return matches.iterator().hasNext();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void write(Path context, String stub) {
        RunFiles.writeFile(context.resolve(stub.endsWith("/") ? stub + "stub" : stub), "");
    }
}
