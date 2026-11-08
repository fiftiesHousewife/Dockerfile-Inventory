package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** {@code ADD}: a URL, a Git repository or an archive to unpack; {@code COPY} for anything else. */
public record Add(List<String> sources, String destination, String chown, String chmod, String checksum,
        boolean keepGitDir, boolean link, boolean jsonForm) implements Instruction {

    public Add {
        sources = List.copyOf(Checks.notEmpty(sources, "an ADD source"));
        Checks.notBlank(destination, "an ADD destination");
    }

    /** {@code Add.of("https://example.com/a.tgz").to("/opt/")}, then any with-method. */
    public static Sources of(String... sources) {
        return new Sources(List.of(sources));
    }

    /** Owned by {@code user:group}, as {@code --chown}. */
    public Add ownedBy(String owner) {
        return new Add(sources, destination, owner, chmod, checksum, keepGitDir, link, jsonForm);
    }

    /** With these permissions, as {@code --chmod}. */
    public Add mode(String mode) {
        return new Add(sources, destination, chown, mode, checksum, keepGitDir, link, jsonForm);
    }

    /** Refused at build unless the download has this digest, as {@code --checksum}. */
    public Add checksum(String digest) {
        return new Add(sources, destination, chown, chmod, digest, keepGitDir, link, jsonForm);
    }

    /** A Git source keeps its {@code .git} directory, as {@code --keep-git-dir}. */
    public Add keepingGitDir() {
        return new Add(sources, destination, chown, chmod, checksum, true, link, jsonForm);
    }

    /** In a layer of its own that later layers do not depend on, as {@code --link}. */
    public Add linked() {
        return new Add(sources, destination, chown, chmod, checksum, keepGitDir, true, jsonForm);
    }

    /** Written in JSON form, needed when a path has spaces. */
    public Add inJsonForm() {
        return new Add(sources, destination, chown, chmod, checksum, keepGitDir, link, true);
    }

    /** What an {@code ADD} fetches, waiting for where to. */
    public record Sources(List<String> sources) {

        public Sources {
            sources = List.copyOf(sources);
        }

        /** Fetches into {@code destination}. */
        public Add to(String destination) {
            return new Add(sources, destination, null, null, null, false, false, false);
        }
    }
}
