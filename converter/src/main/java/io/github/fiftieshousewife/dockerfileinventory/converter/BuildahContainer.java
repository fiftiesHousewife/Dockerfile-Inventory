package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// One long-lived Buildah container, run natively (an emulated Buildah cannot re-exec itself for RUN), with the
// checkouts at /src and the work directory at /work. Builds keep Docker's format, so HEALTHCHECK, ONBUILD and
// SHELL reach the config.
final class BuildahContainer implements ImageComparisonRun.Builder, AutoCloseable {

    static final String IMAGE = "quay.io/buildah/stable:v1.37.0";
    private static final String NAME = "inventory-compare";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path sources;
    private final Path work;
    private final String arch;

    BuildahContainer(Path sources, Path work, String arch) {
        this.sources = sources.toAbsolutePath();
        this.work = work.toAbsolutePath();
        this.arch = arch;
    }

    static BuildahContainer start(Path sources, Path work) {
        String arch = architecture(Processes.output(List.of("docker", "info", "--format", "{{.Architecture}}")));
        BuildahContainer container = new BuildahContainer(sources, work, arch);
        Processes.logged(List.of("docker", "rm", "-f", NAME), container.work.resolve("docker-rm.log"));
        Processes.output(List.of("docker", "run", "-d", "--rm", "--privileged", "--platform", "linux/" + arch,
                "--name", NAME, "-e", "STORAGE_DRIVER=vfs", "-v", container.sources + ":/src",
                "-v", container.work + ":/work", "-v", "inventory-compare-buildah:/var/lib/containers",
                IMAGE, "sleep", "infinity"));
        return container;
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
    public boolean build(Path dockerfile, Path context, String tag, Path log) {
        return Processes.logged(exec("buildah", "build", "-q", "--format", "docker", "--layers=false", "-f",
                inside(dockerfile), "-t", tag, inside(context)), log);
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
