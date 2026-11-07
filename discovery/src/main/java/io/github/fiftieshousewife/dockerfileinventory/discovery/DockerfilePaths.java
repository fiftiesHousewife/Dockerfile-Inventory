package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class DockerfilePaths {

    private static final Pattern DOCKERFILE = Pattern.compile(
            "(^|/)([^/]+\\.)?(Dockerfile|Containerfile)(\\.[^/]+)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NOT_DOCKERFILES = Set.of(
            "go", "py", "sh", "bash", "rb", "js", "ts", "java", "kt", "groovy", "ps1",
            "md", "txt", "json", "yaml", "yml", "xml", "tmpreferences",
            "tpl", "template", "j2", "jinja", "erb", "in", "dockerignore",
            "svg", "png", "jpg", "jpeg", "gif", "ico", "flex", "bnf", "g4", "lex");
    private static final Pattern NOT_BUILT =
            Pattern.compile("(^|/)(vendor|node_modules|third_party|testdata|testfiles|testfiles-negative)/");

    private DockerfilePaths() {
    }

    static boolean isDockerfile(String path) {
        return DOCKERFILE.matcher(path).find() && !NOT_BUILT.matcher(path).find()
                && !NOT_DOCKERFILES.contains(extension(path));
    }

    static String extension(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
