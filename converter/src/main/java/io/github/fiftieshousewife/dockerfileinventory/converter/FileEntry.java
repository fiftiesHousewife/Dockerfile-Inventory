package io.github.fiftieshousewife.dockerfileinventory.converter;

// One path of an image's root file system: its type (find's letter), mode, owner, group, link target and the
// SHA-256 of a regular file's content.
record FileEntry(String type, String mode, String uid, String gid, String target, String sha256) {
}
