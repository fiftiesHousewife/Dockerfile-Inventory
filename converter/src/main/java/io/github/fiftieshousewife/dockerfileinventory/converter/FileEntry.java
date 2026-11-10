package io.github.fiftieshousewife.dockerfileinventory.converter;

record FileEntry(String type, String mode, String uid, String gid, String target, String sha256) {
}
