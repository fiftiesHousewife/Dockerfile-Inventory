package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Run natively: an emulated Buildah cannot re-exec itself for RUN. Builds keep Docker's format, so HEALTHCHECK,
// ONBUILD and SHELL reach the config.
final class BuildahContainer implements ImageComparisonRun.Builder, AutoCloseable {

    static final String IMAGE = "quay.io/buildah/stable:v1.37.0";
    private static final String NAME = "inventory-compare";
    private static final String EMPTY_SECRET = "empty-secret";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path sources;
    private final Path work;
    private final String arch;
    private final int timeoutMinutes;

    BuildahContainer(Path sources, Path work, String arch, int timeoutMinutes) {
        this.sources = sources.toAbsolutePath();
        this.work = work.toAbsolutePath();
        this.arch = arch;
        this.timeoutMinutes = timeoutMinutes;
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
        BuildahContainer container = new BuildahContainer(sources, work, arch, timeoutMinutes(environment));
        Processes.logged(List.of("docker", "rm", "-f", NAME), container.work.resolve("docker-rm.log"));
        Processes.output(container.runCommand(environment));
        return container;
    }

    List<String> runCommand(Map<String, String> environment) {
        List<String> command = new ArrayList<>(List.of("docker", "run", "-d", "--rm", "--privileged", "--platform",
                "linux/" + arch, "--name", NAME, "-e", "STORAGE_DRIVER=vfs", "-v", sources + ":/src",
                "-v", work + ":/work", "-v", "inventory-compare-buildah:/var/lib/containers"));
        setting(environment, "REGISTRY_AUTH_FILE").ifPresent(auth -> command.addAll(
                List.of("-v", auth + ":/auth.json:ro", "-e", "REGISTRY_AUTH_FILE=/auth.json")));
        setting(environment, "REGISTRIES_CONF").ifPresent(conf -> command.addAll(
                List.of("-v", conf + ":/etc/containers/registries.conf:ro")));
        command.addAll(List.of(IMAGE, "sleep", "infinity"));
        return command;
    }

    private static Optional<String> setting(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name)).filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().toString());
    }

    static String architecture(String docker) {
        if (docker.isBlank()) {
            throw new IllegalStateException("Docker reports no architecture: is it running and reachable?");
        }
        return docker.strip().replace("x86_64", "amd64").replace("aarch64", "arm64");
    }

    String arch() {
        return arch;
    }

    @Override
    public BuildOutcome build(Path dockerfile, Path context, String tag, Path log, List<String> secrets) {
        RunFiles.writeFile(work.resolve(EMPTY_SECRET), "");
        int exitStatus = Processes.exitStatus(buildCommand(dockerfile, context, tag, secrets), log, Map.of());
        return BuildOutcome.of(exitStatus, RunFiles.read(log));
    }

    @Override
    public void remove(List<String> tags) {
        tags.forEach(tag -> Processes.logged(removeCommand(tag), work.resolve("remove.log")));
    }

    List<String> removeCommand(String tag) {
        return exec("buildah", "rmi", "-f", tag);
    }

    List<String> buildCommand(Path dockerfile, Path context, String tag, List<String> secrets) {
        List<String> command = new ArrayList<>(exec("timeout", Integer.toString(timeoutMinutes * 60), "buildah",
                "build", "-q", "--format", "docker", "--layers=false"));
        secrets.forEach(id -> command.addAll(List.of("--secret", "id=" + id + ",src=/work/" + EMPTY_SECRET)));
        command.addAll(List.of("-f", inside(dockerfile), "-t", tag, inside(context)));
        return command;
    }

    @Override
    public JsonNode inspect(String tag) {
        return JSON.readTree(Processes.output(exec("buildah", "inspect", "--type", "image", tag)));
    }

    @Override
    public SortedMap<String, FileEntry> files(String tag) {
        String container = Processes.output(exec("buildah", "from", "-q", tag)).strip();
        try {
            String mount = Processes.output(exec("buildah", "mount", container)).strip();
            return Processes.streamed(exec("tar", "-C", mount, "--numeric-owner", "-cf", "-", "."), TarListing::of);
        } finally {
            Processes.output(exec("buildah", "rm", container));
        }
    }

    @Override
    public void close() {
        Processes.output(List.of("docker", "rm", "-f", NAME));
    }

    String inside(Path host) {
        Path path = host.toAbsolutePath().normalize();
        if (path.startsWith(sources)) {
            return "/src/" + sources.relativize(path);
        }
        if (path.startsWith(work)) {
            return "/work/" + work.relativize(path);
        }
        throw new IllegalArgumentException(path + " is under neither " + sources + " nor " + work);
    }

    private static List<String> exec(String... command) {
        List<String> line = new ArrayList<>(List.of("docker", "exec", NAME));
        line.addAll(List.of(command));
        return line;
    }
}
