package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;

/** A recursive git tree listing. When {@code truncated}, the entries are incomplete and the repository needs a clone. */
public record Tree(boolean truncated, List<Entry> entries) {

    public Tree {
        entries = List.copyOf(entries);
    }

    /** {@code mode} is git's: {@code 100644} a file, {@code 120000} a symlink, {@code 040000} a directory. */
    public record Entry(String path, String mode, String type, String sha) {

        public boolean isBlob() {
            return "blob".equals(type);
        }

        /** Git stores a symlink as a blob holding its target's path, which is not the file it names. */
        public boolean isSymlink() {
            return "120000".equals(mode);
        }

        /** This entry as seen from the parent directory {@code prefix}. */
        Entry under(String prefix) {
            return new Entry(prefix + "/" + path, mode, type, sha);
        }

        /** A directory. Submodules are type {@code commit} and are neither blobs nor trees. */
        public boolean isTree() {
            return "tree".equals(type);
        }
    }
}
