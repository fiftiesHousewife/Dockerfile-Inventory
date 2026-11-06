package io.github.fiftieshousewife.dockerfileinventory.report;

/** A Dockerfile's final base image as written, after build-argument substitution. */
public record Base(String name, String tag, String digest) {
}
