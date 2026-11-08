package io.github.fiftieshousewife.dockerfile.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

abstract sealed class MetadataInstructionBuilder extends FileInstructionBuilder permits InstructionBuilder {

    MetadataInstructionBuilder() {
    }

    /** One TCP port the image listens on. */
    public ImageBuilder expose(int port) {
        return add(new Expose(List.of(Port.of(port))));
    }

    /** Ports as Docker writes them: {@code "8080"}, {@code "53/udp"}, {@code "8000-8010"}, {@code "${PORT}"}. */
    public ImageBuilder expose(String... specs) {
        return add(new Expose(Arrays.stream(specs).map(Port::new).toList()));
    }

    /** A check run as an exec-form command; options as Docker names them, such as {@code interval}. */
    public ImageBuilder healthcheck(Map<String, String> options, String... command) {
        return healthcheck(options, Arrays.asList(command), CommandForm.EXEC);
    }

    /** A {@code HEALTHCHECK} as written, for the faithful path. */
    public ImageBuilder healthcheck(Map<String, String> options, List<String> command, CommandForm form) {
        return add(new Healthcheck(options, command, form));
    }

    /** {@code HEALTHCHECK NONE}: turns off a check inherited from the base. */
    public ImageBuilder healthcheckNone() {
        return add(Healthcheck.NONE);
    }

    /** The shell later shell-form instructions run with. */
    public ImageBuilder shell(String... command) {
        return add(new Shell(List.of(command)));
    }

    /** {@code VOLUME ["/data"]}. */
    public ImageBuilder volume(String... paths) {
        return volume(List.of(paths), true);
    }

    /** A {@code VOLUME} in exec form ({@code ["/data"]}) or as plain words. */
    public ImageBuilder volume(List<String> paths, boolean jsonForm) {
        return add(new Volume(paths, jsonForm));
    }

    /** The signal the runtime sends to stop the container. */
    public ImageBuilder stopSignal(String signal) {
        return add(new StopSignal(signal));
    }

    /** For converted Dockerfiles only: Docker deprecates {@code MAINTAINER}; a new image says it with a label. */
    public ImageBuilder maintainer(String name) {
        return add(new Maintainer(name));
    }
}
