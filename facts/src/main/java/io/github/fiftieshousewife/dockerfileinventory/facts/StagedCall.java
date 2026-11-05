package io.github.fiftieshousewife.dockerfileinventory.facts;

/** A package call and the index of the stage it runs in; the last stage is the image that ships. */
public record StagedCall(int stage, PackageCall call) {
}
