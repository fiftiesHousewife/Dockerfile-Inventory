package io.github.fiftieshousewife.dockerfileinventory.rules;

final class TrailingSlash {

    private TrailingSlash() {
    }

    static String removed(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
