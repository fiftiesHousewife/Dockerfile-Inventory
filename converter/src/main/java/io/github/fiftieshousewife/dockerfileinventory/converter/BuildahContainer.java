package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class BuildahContainer implements ImageComparisonRun.Builder, AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BuildahCommands commands;

    BuildahContainer(BuildahCommands commands) {
        this.commands = commands;
    }

    static int timeoutMinutes(Map<String, String> environment) {
        String value = environment.get("BUILD_TIMEOUT");
        if (value == null) {
            return 10;
        }
        if (!value.matches("[1-9][0-9]{0,4}")) {
            throw new IllegalStateException("BUILD_TIMEOUT must be a positive whole number of minutes, not " + value);
        }
        return Integer.parseInt(value);
    }

    static BuildahContainer start(Path sources, Path work, Map<String, String> environment) {
        String arch = architecture(Processes.output(List.of("docker", "info", "--format", "{{.Architecture}}")));
        BuildahCommands commands = new BuildahCommands(sources, work, arch, timeoutMinutes(environment));
        Processes.logged(BuildahCommands.stop(), commands.work().resolve("docker-rm.log"));
        Processes.output(commands.start(environment));
        return new BuildahContainer(commands);
    }

    static String architecture(String docker) {
        if (docker.isBlank()) {
            throw new IllegalStateException("Docker reports no architecture: is it running and reachable?");
        }
        return docker.strip().replace("x86_64", "amd64").replace("aarch64", "arm64");
    }

    String arch() {
        return commands.arch();
    }

    @Override
    public BuildOutcome build(Path dockerfile, Path context, String tag, Path log, List<String> secrets) {
        RunFiles.writeFile(commands.work().resolve(BuildahCommands.EMPTY_SECRET), "");
        return retried(() -> BuildOutcome.of(Processes.exitStatus(commands.build(dockerfile, context, tag, secrets),
                log, Map.of()), RunFiles.read(log)), log);
    }

    static BuildOutcome retried(Supplier<BuildOutcome> attempt, Path log) {
        BuildOutcome first = attempt.get();
        if (first != BuildOutcome.FAILED || !BuildOutcome.fetchFailed(RunFiles.read(log))) {
            return first;
        }
        RunFiles.writeFile(Path.of(log.toString().replaceFirst("\\.log$", ".first.log")), RunFiles.read(log));
        return attempt.get();
    }

    @Override
    public void remove(List<String> tags) {
        tags.forEach(tag -> Processes.logged(BuildahCommands.remove(tag), removeLog()));
    }

    @Override
    public JsonNode inspect(String tag) {
        return JSON.readTree(Processes.output(BuildahCommands.inspect(tag)));
    }

    @Override
    public SortedMap<String, FileEntry> files(String tag) {
        String container = Processes.output(BuildahCommands.from(tag)).strip();
        try {
            String mount = Processes.output(BuildahCommands.mount(container)).strip();
            return Processes.streamed(BuildahCommands.tar(mount), TarListing::of);
        } finally {
            Processes.output(BuildahCommands.removeContainer(container));
        }
    }

    @Override
    public void close() {
        BuildahCommands.clearing().forEach(command -> Processes.logged(command, removeLog()));
        Processes.output(BuildahCommands.stop());
    }

    private Path removeLog() {
        return commands.work().resolve("remove.log");
    }
}
