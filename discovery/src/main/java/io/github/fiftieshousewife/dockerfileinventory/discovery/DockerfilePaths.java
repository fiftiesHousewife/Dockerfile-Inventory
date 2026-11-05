package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.regex.Pattern;

/**
 * Decides which tree entries are Dockerfiles: {@code Dockerfile}, {@code Containerfile}, and their
 * suffixed and prefixed variants ({@code Dockerfile.prod}, {@code app.Dockerfile}), in any directory.
 */
public final class DockerfilePaths {

    private static final Pattern DOCKERFILE = Pattern.compile(
            "(^|/)([^/]+\\.)?(Dockerfile|Containerfile)(\\.[^/]+)?$", Pattern.CASE_INSENSITIVE);

    private DockerfilePaths() {
    }

    public static boolean isDockerfile(String path) {
        return DOCKERFILE.matcher(path).find();
    }
}
