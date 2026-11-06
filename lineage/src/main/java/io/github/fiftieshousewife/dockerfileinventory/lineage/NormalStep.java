package io.github.fiftieshousewife.dockerfileinventory.lineage;

/**
 * One history step in a form that can be compared with a Dockerfile instruction: its keyword, and the part of it
 * a Dockerfile determines. {@code from} is the base reference Buildah records on a build's first step;
 * {@code builderAdded} marks steps a build service adds that no Dockerfile has.
 */
public record NormalStep(String keyword, String text, String from, boolean builderAdded) {

    NormalStep withFrom(String reference) {
        return new NormalStep(keyword, text, reference, builderAdded);
    }
}
