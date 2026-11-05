package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;

/** A recursive git tree listing. When {@code truncated}, the entries are incomplete and the repository needs a clone. */
public record Tree(boolean truncated, List<Entry> entries) {

    public Tree {
        entries = List.copyOf(entries);
    }

    public record Entry(String path, String type, String sha) {

        public boolean isBlob() {
            return "blob".equals(type);
        }

        /** This entry as seen from the parent directory {@code prefix}. */
        Entry under(String prefix) {
            return new Entry(prefix + "/" + path, type, sha);
        }

        /** A directory. Submodules are type {@code commit} and are neither blobs nor trees. */
        public boolean isTree() {
            return "tree".equals(type);
        }
    }
}
