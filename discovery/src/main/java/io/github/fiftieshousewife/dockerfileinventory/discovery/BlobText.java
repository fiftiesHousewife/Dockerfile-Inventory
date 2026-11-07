package io.github.fiftieshousewife.dockerfileinventory.discovery;

record BlobText(String sha, String text, boolean truncated, boolean binary) {
}
