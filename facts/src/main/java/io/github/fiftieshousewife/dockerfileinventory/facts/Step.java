package io.github.fiftieshousewife.dockerfileinventory.facts;

/**
 * One instruction of the final image in the normal form Buildah's history reduces to, so lineage can match an
 * image to the Dockerfile that built it. The contract is {@code testdata/contracts/}.
 */
public record Step(String keyword, String text) {
}
