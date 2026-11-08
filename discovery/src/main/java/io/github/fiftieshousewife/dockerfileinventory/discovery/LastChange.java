package io.github.fiftieshousewife.dockerfileinventory.discovery;

record LastChange(String repository, String commitSha, String path, String changedIn, String changedAt) {
}
