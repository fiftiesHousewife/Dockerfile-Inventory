package io.github.fiftieshousewife.dockerfileinventory.validator;

record Finding(Check check, Severity severity, Certainty certainty, String blobSha, String message) {
}
