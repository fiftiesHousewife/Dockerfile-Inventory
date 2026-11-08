package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// A build context's missing COPY and ADD sources as empty files, for sources CI makes before the image is built,
// so the original and the regenerated Dockerfile can both be built on the same inputs.
final class Stubs {

    private Stubs() {
    }

    static List<String> create(Path context, List<String> sources, String arch) {
        List<String> created = new ArrayList<>();
        for (String source : sources) {
            String path = expanded(source, arch);
            if (!exists(context, path)) {
                String stub = path.replace("*", "stub").replace("?", "s");
                write(context, stub);
                created.add(stub);
            }
        }
        return created;
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

    private static void write(Path context, String stub) {
        RunFiles.writeFile(context.resolve(stub.endsWith("/") ? stub + "stub" : stub), "");
    }
}
