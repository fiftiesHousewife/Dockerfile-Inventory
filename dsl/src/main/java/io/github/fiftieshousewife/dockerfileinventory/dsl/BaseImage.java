package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.regex.Pattern;

/**
 * The image a stage builds on: name (with registry), tag, and digest. A digest, when given, is {@code sha256:} and
 * 64 hex characters; the builders require one (R1), the model does not, so Phase 5 can model what exists.
 */
public record BaseImage(String name, String tag, String digest) {

    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    public BaseImage {
        Checks.notBlank(name, "base image name");
        if (digest != null && !DIGEST.matcher(digest).matches()) {
            throw new IllegalArgumentException("a digest is sha256: and 64 hex characters, not " + digest);
        }
    }

    /** The last path segment, the base's family as Phase 1 counts it ({@code ubi-minimal}, {@code alpine}). */
    public String family() {
        return name.substring(name.lastIndexOf('/') + 1);
    }
}
