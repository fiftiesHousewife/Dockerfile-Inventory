package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class Checkouts {

    private final Path root;
    private final CloneSource source;

    Checkouts(Path root, CloneSource source) {
        this.root = root;
        this.source = source;
    }

    Path checkout(String repository, String commit) {
        Path directory = root.resolve(repository).resolve(commit);
        String at = directory.toString();
        if (!Files.isDirectory(directory.resolve(".git"))) {
            Map<String, String> git = source.gitEnvironment();
            Processes.output(List.of("git", "clone", "-q", "--filter=blob:none", "--no-checkout",
                    source.url(repository), at), git);
            Processes.output(List.of("git", "-C", at, "checkout", "-q", commit), git);
            Processes.logged(List.of("git", "-C", at, "submodule", "update", "-q", "--init", "--recursive"),
                    directory.resolveSibling(commit + "-submodules.log"), git);
        }
        Processes.output(List.of("git", "-C", at, "clean", "-q", "-f", "-d", "-x"));
        return directory;
    }
}
