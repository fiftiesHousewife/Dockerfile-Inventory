package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class Processes {

    interface Reader<T> {
        T read(InputStream output) throws IOException;
    }

    private Processes() {
    }

    static String output(List<String> command) {
        return output(command, Map.of());
    }

    static String output(List<String> command, Map<String, String> environment) {
        return streamed(command, environment, output -> new String(output.readAllBytes(), StandardCharsets.UTF_8));
    }

    static <T> T streamed(List<String> command, Reader<T> reader) {
        return streamed(command, Map.of(), reader);
    }

    static <T> T streamed(List<String> command, Map<String, String> environment, Reader<T> reader) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().putAll(environment);
            Process process = builder.start();
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
        return logged(command, log, Map.of());
    }

    static boolean logged(List<String> command, Path log, Map<String, String> environment) {
        try {
            RunFiles.createParent(log);
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().putAll(environment);
            Process process = builder.start();
            return process.waitFor() == 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted: " + String.join(" ", command), e);
        }
    }
}
