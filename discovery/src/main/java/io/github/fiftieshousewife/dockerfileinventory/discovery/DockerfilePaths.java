package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which tree entries are Dockerfiles: {@code Dockerfile}, {@code Containerfile}, and their suffixed and
 * prefixed variants ({@code Dockerfile.rhel9}, {@code app.Dockerfile}), in any directory. Not counted: files
 * named like Dockerfiles but holding source, documents or templates ({@code dockerfile.go}, {@code Dockerfile.tpl}),
 * and anything in a third-party copy ({@code vendor/}, {@code node_modules/}, {@code third_party/}), which is
 * someone else's image, not the estate's.
 */
public final class DockerfilePaths {

    private static final Pattern DOCKERFILE = Pattern.compile(
            "(^|/)([^/]+\\.)?(Dockerfile|Containerfile)(\\.[^/]+)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NOT_DOCKERFILES = Set.of("go", "md", "txt", "json", "yaml", "yml", "xml",
            "tmpreferences", "tpl", "template", "j2", "jinja", "erb", "in");
    private static final Pattern THIRD_PARTY = Pattern.compile("(^|/)(vendor|node_modules|third_party)/");

    private DockerfilePaths() {
    }

    public static boolean isDockerfile(String path) {
        return DOCKERFILE.matcher(path).find() && !THIRD_PARTY.matcher(path).find()
                && !NOT_DOCKERFILES.contains(extension(path));
    }

    private static String extension(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
