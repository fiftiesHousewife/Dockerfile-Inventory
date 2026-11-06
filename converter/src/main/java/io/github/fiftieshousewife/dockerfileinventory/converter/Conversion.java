package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;

/** A Dockerfile converted to the model. */
public record Conversion(Dockerfile model) {
}
