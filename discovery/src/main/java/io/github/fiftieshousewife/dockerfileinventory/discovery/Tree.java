package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;

record Tree(boolean truncated, List<Entry> entries) {

    Tree {
        entries = List.copyOf(entries);
    }

    record Entry(String path, String mode, String type, String sha) {

        boolean isBlob() {
            return "blob".equals(type);
        }

        // Git stores a symlink as a blob holding its target's path: not the Dockerfile it is named like.
        boolean isSymlink() {
            return "120000".equals(mode);
        }

        // Submodules are type "commit": neither blobs nor trees.
        boolean isTree() {
            return "tree".equals(type);
        }

        Entry under(String prefix) {
            return new Entry(prefix + "/" + path, mode, type, sha);
        }
    }
}
