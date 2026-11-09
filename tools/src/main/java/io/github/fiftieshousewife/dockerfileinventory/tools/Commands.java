package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

interface Commands {

    record Result(int exit, String out) {

        boolean ok() {
            return exit == 0;
        }
    }

    Result run(List<String> command, String stdin);

    default Result run(String... command) {
        return run(List.of(command), null);
    }

    // stderr joins stdout, so a failure says why.
    static Commands processes() {
        return (command, stdin) -> {
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                if (stdin != null) {
                    process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
                }
                process.getOutputStream().close();
                String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return new Result(process.waitFor(), out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };
    }
}
