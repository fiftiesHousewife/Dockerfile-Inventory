package io.github.fiftieshousewife.dockerfileinventory.lineage;

/** Image references reduced to the path that compares across registries, tags and digests. */
final class ImagePaths {

    private ImagePaths() {
    }

    /**
     * {@code registry.example.com:5000/ubi9/s2i-base:9.8@sha256:...} gives {@code ubi9/s2i-base}. As in Docker, a first
     * segment is a registry host only if it holds a dot or a colon, or is {@code localhost}; facts' {@code fromPath}
     * applies the same rule.
     */
    static String path(String reference) {
        int at = reference.indexOf('@');
        String name = at < 0 ? reference : reference.substring(0, at);
        int colon = name.lastIndexOf(':');
        if (colon > name.lastIndexOf('/')) {
            name = name.substring(0, colon);
        }
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        boolean host = first.contains(".") || first.contains(":") || first.equals("localhost");
        return host ? name.substring(slash + 1) : name;
    }
}
