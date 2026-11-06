package io.github.fiftieshousewife.dockerfileinventory.lineage;

/** An effective value and the image in the chain that set it. */
public record Property<T>(T value, SetBy setBy) {
}
