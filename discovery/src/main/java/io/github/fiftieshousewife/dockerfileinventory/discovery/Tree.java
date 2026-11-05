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
    }
}
