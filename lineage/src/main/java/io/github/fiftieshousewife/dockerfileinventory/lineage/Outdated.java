package io.github.fiftieshousewife.dockerfileinventory.lineage;

/**
 * An image built on a base that has since been pushed again under the same image name (Q12): {@code newer} is the
 * newest image with that name, which a rebuild of {@code image} would pick up.
 */
public record Outdated(String image, String parent, String imageName, String newer) {
}
