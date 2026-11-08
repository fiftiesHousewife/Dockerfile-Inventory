package io.github.fiftieshousewife.dockerfileinventory.converter;

// The type is find's %y letter.
record FileEntry(String type, String mode, String uid, String gid, String target, String sha256) {
}
