package io.github.fiftieshousewife.dockerfileinventory.validator;

record ImageReference(String path, String tag, String digest) {

    static final String LATEST = "latest";

    static ImageReference of(String reference) {
        int at = reference.indexOf('@');
        String named = at < 0 ? reference : reference.substring(0, at);
        String digest = at < 0 ? null : reference.substring(at + 1);
        int slash = named.indexOf('/');
        String path = slash >= 0 && host(named.substring(0, slash)) ? named.substring(slash + 1) : named;
        int colon = path.lastIndexOf(':');
        return colon > path.lastIndexOf('/') ? new ImageReference(path.substring(0, colon),
                path.substring(colon + 1), digest) : new ImageReference(path, null, digest);
    }

    static boolean host(String segment) {
        return segment.contains(".") || segment.contains(":") || segment.equals("localhost");
    }

    boolean floating() {
        return digest == null && tagOrLatest().equals(LATEST);
    }

    String tagOrLatest() {
        return tag == null ? LATEST : tag;
    }
}
