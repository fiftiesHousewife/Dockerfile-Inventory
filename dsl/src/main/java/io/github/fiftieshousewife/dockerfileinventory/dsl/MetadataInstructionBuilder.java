package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

abstract sealed class MetadataInstructionBuilder extends FileInstructionBuilder permits InstructionBuilder {

    MetadataInstructionBuilder() {
    }

    public ImageBuilder expose(int port) {
        return add(new Instruction.Expose(List.of(Port.of(port))));
    }

    /** Ports as Docker writes them: {@code "8080"}, {@code "53/udp"}, {@code "8000-8010"}, {@code "${PORT}"}. */
    public ImageBuilder expose(String... specs) {
        return add(new Instruction.Expose(Arrays.stream(specs).map(Port::new).toList()));
    }

    /** A check run as an exec-form command; options as Docker names them, such as {@code interval}. */
    public ImageBuilder healthcheck(Map<String, String> options, String... command) {
        return healthcheck(options, Arrays.asList(command), CommandForm.EXEC);
    }

    /** A {@code HEALTHCHECK} as written, for the faithful path. */
    public ImageBuilder healthcheck(Map<String, String> options, List<String> command, CommandForm form) {
        return add(new Instruction.Healthcheck(options, command, form));
    }

    public ImageBuilder healthcheckNone() {
        return add(Instruction.Healthcheck.NONE);
    }

    public ImageBuilder shell(String... command) {
        return add(new Instruction.Shell(List.of(command)));
    }

    /** {@code VOLUME ["/data"]}. */
    public ImageBuilder volume(String... paths) {
        return volume(List.of(paths), true);
    }

    public ImageBuilder volume(List<String> paths, boolean jsonForm) {
        return add(new Instruction.Volume(paths, jsonForm));
    }

    public ImageBuilder stopSignal(String signal) {
        return add(new Instruction.StopSignal(signal));
    }

    /** For converted Dockerfiles only: Docker deprecates {@code MAINTAINER}; a new image says it with a label. */
    public ImageBuilder maintainer(String name) {
        return add(new Instruction.Maintainer(name));
    }
}
