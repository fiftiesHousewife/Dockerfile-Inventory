package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The final image's configuration as its Dockerfile writes it, through the stages it is built on by name. A value
 * that is secret-shaped, or stands under a secret's name, is {@code (hidden sha256:...)}; nothing secret is kept.
 */
public record ImageConfig(String workdir, List<String> volumes, String healthcheck, List<String> shell,
        String stopSignal, Command entrypoint, Command cmd, Map<String, String> environment,
        Map<String, String> labels, int layers) {

    /** An entrypoint or command: exec form's words, or shell form's one string. */
    public record Command(EntrypointForm form, List<String> arguments) {

        public Command {
            arguments = List.copyOf(arguments);
        }
    }

    public ImageConfig {
        volumes = List.copyOf(volumes);
        shell = List.copyOf(shell);
        environment = Collections.unmodifiableMap(new LinkedHashMap<>(environment));
        labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
    }
}
