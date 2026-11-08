package io.github.fiftieshousewife.dockerfileinventory.discovery;

record ChangeRequest(String repository, String commitSha, String path) {
}
