package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

final class Processes {

    interface Reader<T> {
        T read(InputStream output) throws IOException;
    }

    private Processes() {
    }

    static String output(List<String> command) {
        return streamed(command, output -> new String(output.readAllBytes(), StandardCharsets.UTF_8));
    }

    static <T> T streamed(List<String> command, Reader<T> reader) {
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            T read;
            try (InputStream output = process.getInputStream()) {
                read = reader.read(output);
                output.transferTo(OutputStream.nullOutputStream());
            }
            if (process.waitFor() != 0) {
                throw new IllegalStateException("failed: " + String.join(" ", command));
            }
            return read;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted: " + String.join(" ", command), e);
        }
    }

    static boolean logged(List<String> command, Path log) {
        try {
            RunFiles.createParent(log);
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).start();
            return process.waitFor() == 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted: " + String.join(" ", command), e);
        }
    }
}
