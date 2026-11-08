package io.github.fiftieshousewife.dockerfile.model;

import java.util.regex.Pattern;

/**
 * The image a stage builds on. A digest may be a build argument ({@code sha256:${BASE_SHA}}) and may be absent: the
 * builder's policy requires a real one, the model does not, so a converter can model the Dockerfiles that exist.
 */
public record BaseImage(String name, String tag, String digest) {

    private static final Pattern DIGEST =
            Pattern.compile("sha256:([0-9a-f]{64}|\\$\\{[A-Za-z_][A-Za-z0-9_]*}|\\$[A-Za-z_][A-Za-z0-9_]*)");

    public BaseImage {
        Checks.notBlank(name, "base image name");
        if (digest != null && !DIGEST.matcher(digest).matches()) {
            throw new IllegalArgumentException(
                    "a digest is sha256: and 64 hex characters (or a build argument), not " + digest);
        }
    }

    /** The last path segment, the family the inventory counts bases by ({@code ubi-minimal}, {@code alpine}). */
    public String family() {
        return name.substring(name.lastIndexOf('/') + 1);
    }
}
