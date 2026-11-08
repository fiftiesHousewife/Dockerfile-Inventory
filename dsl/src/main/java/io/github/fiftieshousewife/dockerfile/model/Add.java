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

    public Add ownedBy(String owner) {
        return new Add(sources, destination, owner, chmod, checksum, keepGitDir, link, jsonForm);
    }

    public Add mode(String mode) {
        return new Add(sources, destination, chown, mode, checksum, keepGitDir, link, jsonForm);
    }

    public Add checksum(String digest) {
        return new Add(sources, destination, chown, chmod, digest, keepGitDir, link, jsonForm);
    }

    public Add keepingGitDir() {
        return new Add(sources, destination, chown, chmod, checksum, true, link, jsonForm);
    }

    public Add linked() {
        return new Add(sources, destination, chown, chmod, checksum, keepGitDir, true, jsonForm);
    }

    public Add inJsonForm() {
        return new Add(sources, destination, chown, chmod, checksum, keepGitDir, link, true);
    }

    /** What an {@code ADD} fetches, waiting for where to. */
    public record Sources(List<String> sources) {

        public Sources {
            sources = List.copyOf(sources);
        }

        public Add to(String destination) {
            return new Add(sources, destination, null, null, null, false, false, false);
        }
    }
}
