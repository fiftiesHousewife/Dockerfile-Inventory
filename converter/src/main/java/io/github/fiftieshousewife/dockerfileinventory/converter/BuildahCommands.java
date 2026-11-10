package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class BuildahCommands {

    static final String IMAGE = "quay.io/buildah/stable:v1.43.5";
    static final String NAME = "inventory-compare";
    static final String EMPTY_SECRET = "empty-secret";

    private final Path sources;
    private final Path work;
    private final String arch;
    private final int timeoutMinutes;

    BuildahCommands(Path sources, Path work, String arch, int timeoutMinutes) {
        this.sources = sources.toAbsolutePath();
        this.work = work.toAbsolutePath();
        this.arch = arch;
        this.timeoutMinutes = timeoutMinutes;
    }

    Path work() {
        return work;
    }

    String arch() {
        return arch;
    }

    // An emulated Buildah cannot re-exec itself for RUN, so it runs on Docker's own platform.
    List<String> start(Map<String, String> environment) {
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

    static Optional<String> setting(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name)).filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().toString());
    }

    static List<String> stop() {
        return List.of("docker", "rm", "-f", NAME);
    }

    // Docker's format, unlike OCI's, keeps HEALTHCHECK, ONBUILD and SHELL in the config.
    List<String> build(Path dockerfile, Path context, String tag, List<String> secrets) {
        List<String> command = new ArrayList<>(exec("timeout", "-k", "30", Integer.toString(timeoutMinutes * 60),
                "buildah", "build", "-q", "--format", "docker", "--layers=false"));
        secrets.forEach(id -> command.addAll(List.of("--secret", "id=" + id + ",src=/work/" + EMPTY_SECRET)));
        command.addAll(List.of("-f", inside(dockerfile), "-t", tag, inside(context)));
        return command;
    }

    static List<String> remove(String tag) {
        return exec("buildah", "rmi", "-f", tag);
    }

    static List<List<String>> clearing() {
        return List.of(exec("buildah", "rm", "--all"), exec("buildah", "rmi", "--prune"));
    }

    static List<String> inspect(String tag) {
        return exec("buildah", "inspect", "--type", "image", tag);
    }

    static List<String> from(String tag) {
        return exec("buildah", "from", "-q", tag);
    }

    static List<String> mount(String container) {
        return exec("buildah", "mount", container);
    }

    static List<String> tar(String mount) {
        return exec("tar", "-C", mount, "--numeric-owner", "-cf", "-", ".");
    }

    static List<String> removeContainer(String container) {
        return exec("buildah", "rm", container);
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

    static List<String> exec(String... command) {
        List<String> line = new ArrayList<>(List.of("docker", "exec", NAME));
        line.addAll(List.of(command));
        return line;
    }
}
