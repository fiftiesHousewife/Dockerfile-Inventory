package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Public GitHub repositories cloned at a commit, kept for the next run and cleaned of anything untracked (the
// stubs of the last comparison) each time they are used.
final class Checkouts {

    private final Path root;

    Checkouts(Path root) {
        this.root = root;
    }

    Path checkout(String repository, String commit) {
        Path directory = root.resolve(repository).resolve(commit);
        String at = directory.toString();
        if (!Files.isDirectory(directory.resolve(".git"))) {
            Processes.output(List.of("git", "clone", "-q", "--filter=blob:none", "--no-checkout",
                    "https://github.com/" + repository + ".git", at));
            Processes.output(List.of("git", "-C", at, "checkout", "-q", commit));
            Processes.logged(List.of("git", "-C", at, "submodule", "update", "-q", "--init", "--recursive"),
                    directory.resolveSibling(commit + "-submodules.log"));
        }
        Processes.output(List.of("git", "-C", at, "clean", "-q", "-f", "-d", "-x"));
        return directory;
    }
}
